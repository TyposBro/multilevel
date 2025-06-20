const { createClient } = require("@supabase/supabase-js");
const admin = require("firebase-admin");
const { getStorage } = require("firebase-admin/storage");

// --- Supabase Client Initialization ---
const supabaseUrl = process.env.SUPABASE_URL;
const supabaseKey = process.env.SUPABASE_KEY;
if (!supabaseUrl || !supabaseKey) {
  console.warn("[Storage Service] Supabase URL or Key not found. Supabase uploads will fail.");
}
const supabase = createClient(supabaseUrl, supabaseKey);

// --- Firebase Admin SDK Initialization ---
try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
    });
    console.log("[Storage Service] Firebase Admin SDK initialized.");
  } else {
    console.warn(
      "[Storage Service] FIREBASE_SERVICE_ACCOUNT_JSON not found. Firebase uploads will fail."
    );
  }
} catch (error) {
  console.error("[Storage Service] Failed to initialize Firebase Admin SDK:", error.message);
}

/**
 * Supabase Upload Strategy
 * @param {object} file - The file object from multer (req.file)
 * @param {string} destinationPath - The folder path in the bucket (e.g., 'images' or 'audio')
 * @returns {Promise<string>} The public URL of the uploaded file
 */
const uploadToSupabase = async (file, destinationPath) => {
  if (!supabaseUrl) throw new Error("Supabase client not initialized.");

  const fileName = `${Date.now()}-${file.originalname.replace(/\s/g, "_")}`;
  const filePath = `${destinationPath}/${fileName}`;
  const bucketName = "multilevel"; // Your bucket name in Supabase

  const { data, error } = await supabase.storage.from(bucketName).upload(filePath, file.buffer, {
    contentType: file.mimetype,
    upsert: false,
  });

  if (error) {
    console.error("Supabase Upload Error:", error);
    throw new Error("Failed to upload file to Supabase.");
  }

  const {
    data: { publicUrl },
  } = supabase.storage.from(bucketName).getPublicUrl(data.path);
  console.log(`[Supabase] File uploaded successfully: ${publicUrl}`);
  return publicUrl;
};

/**
 * Firebase Cloud Storage Upload Strategy
 * @param {object} file - The file object from multer (req.file)
 * @param {string} destinationPath - The folder path in the bucket (e.g., 'images' or 'audio')
 * @returns {Promise<string>} The public URL of the uploaded file
 */
const uploadToFirebase = async (file, destinationPath) => {
  if (!admin.apps.length) throw new Error("Firebase Admin SDK not initialized.");

  const bucket = getStorage().bucket();
  const fileName = `${Date.now()}-${file.originalname.replace(/\s/g, "_")}`;
  const filePath = `${destinationPath}/${fileName}`;
  const blob = bucket.file(filePath);

  const blobStream = blob.createWriteStream({
    metadata: {
      contentType: file.mimetype,
    },
  });

  return new Promise((resolve, reject) => {
    blobStream.on("error", (err) => {
      console.error("Firebase Upload Error:", err);
      reject("Failed to upload file to Firebase.");
    });

    blobStream.on("finish", async () => {
      // Make the file publicly readable.
      await blob.makePublic();
      const publicUrl = `https://storage.googleapis.com/${bucket.name}/${blob.name}`;
      console.log(`[Firebase] File uploaded successfully: ${publicUrl}`);
      resolve(publicUrl);
    });

    blobStream.end(file.buffer);
  });
};

/**
 * The main upload function that acts as a strategy selector.
 * @param {string} provider - 'supabase' or 'firebase'
 * @param {object} file - The file object from multer
 * @param {string} destinationPath - The subfolder for the file
 * @returns {Promise<string>} The public URL of the uploaded file
 */
const uploadToCDN = (provider, file, destinationPath) => {
  switch (provider.toLowerCase()) {
    case "firebase":
      return uploadToFirebase(file, destinationPath);
    case "supabase":
      return uploadToSupabase(file, destinationPath);
    default:
      console.error(`Invalid storage provider: ${provider}. Defaulting to placeholder.`);
      // Fallback placeholder logic
      return Promise.resolve(
        `https://placeholder-cdn.com/${destinationPath}/${Date.now()}-${file.originalname}`
      );
  }
};

module.exports = { uploadToCDN };
