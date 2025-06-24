const mongoose = require("mongoose");
const dotenv = require("dotenv");
const Admin = require("../models/AdminModel"); // Adjust path if needed

dotenv.config({ path: "../.env" }); // Load environment variables

const connectDB = async () => {
  try {
    await mongoose.connect(process.env.MONGO_URI);
    console.log("MongoDB Connected...");
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exit(1);
  }
};

const createAdmin = async () => {
  await connectDB();

  try {
    const email = process.argv[2];
    const password = process.argv[3];

    if (!email || !password) {
      console.error("Please provide an email and password.");
      console.log("Usage: node scripts/createAdmin.js <email> <password>");
      process.exit(1);
    }

    const adminExists = await Admin.findOne({ email });

    if (adminExists) {
      console.error("An admin with this email already exists.");
      process.exit(1);
    }

    const admin = await Admin.create({
      email,
      password,
    });

    console.log("Admin User Created Successfully!");
    console.log(`Email: ${admin.email}`);
    process.exit(0);
  } catch (error) {
    console.error(`Error creating admin: ${error.message}`);
    process.exit(1);
  }
};

createAdmin();
