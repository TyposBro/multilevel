// {PATH_TO_PROJECT}/src/services/storageService.js

/**
 * Cloudflare R2 Upload Strategy
 * @param {object} c - The Hono context, containing environment bindings.
 * @param {File} file - The file object from `c.req.formData()`.
 * @param {string} destinationPath - The folder path in the R2 bucket (e.g., 'images', 'audio').
 * @returns {Promise<string>} The public URL of the uploaded file.
 */
const uploadToR2 = async (c, file, destinationPath) => {
  const R2_BUCKET = c.env.CDN_BUCKET;
  if (!R2_BUCKET) {
    throw new Error("Cloudflare R2 bucket binding 'CDN_BUCKET' not found in wrangler.toml.");
  }

  const fileName = `${Date.now()}-${file.name.replace(/\s/g, "_")}`;
  const filePath = `${destinationPath}/${fileName}`;

  // Put the object (file buffer) into the R2 bucket
  await R2_BUCKET.put(filePath, await file.arrayBuffer(), {
    httpMetadata: { contentType: file.type },
  });

  // Construct the public URL. This requires you to have set up a public domain
  // for your R2 bucket and configured the R2_PUBLIC_URL variable in wrangler.toml.
  const publicUrl = `${c.env.R2_PUBLIC_URL}/${filePath}`;
  console.log(`[R2] File uploaded successfully: ${publicUrl}`);
  return publicUrl;
};

/**
 * The main upload function that acts as a strategy selector.
 * In this Cloudflare-native version, we primarily use the 'cloudflare' provider.
 * @param {object} c - The Hono context.
 * @param {string} provider - The name of the storage provider (should be 'cloudflare').
 * @param {File} file - The file object from `c.req.formData()`.
 * @param {string} destinationPath - The subfolder for the file.
 * @returns {Promise<string>} The public URL of the uploaded file.
 */
export const uploadToCDN = (c, provider, file, destinationPath) => {
  switch (provider.toLowerCase()) {
    case "cloudflare":
      return uploadToR2(c, file, destinationPath);
    // Note: To use Supabase or Firebase, you would need to implement their REST APIs
    // using `fetch`, as their Node.js SDKs are not compatible with Cloudflare Workers.
    default:
      throw new Error(`Invalid or unsupported storage provider: ${provider}. Use 'cloudflare'.`);
  }
};
