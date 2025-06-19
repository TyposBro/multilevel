const mongoose = require("mongoose");
const bcrypt = require("bcryptjs");

const userSchema = new mongoose.Schema({
  email: {
    type: String,
    required: [true, "Please provide an email"],
    unique: true,
    lowercase: true,
    match: [
      /^\w+([\.-]?\w+)*@\w+([\.-]?\w+)*(\.\w{2,3})+$/,
      "Please provide a valid email address",
    ],
    trim: true,
  },
  password: {
    type: String,
    // --- CHANGE: Password is no longer universally required ---
    // required: [true, "Please provide a password"], // REMOVE THIS LINE
    minlength: 6,
    select: false,
  },
  // --- NEW: Add a field to track the auth method ---
  authProvider: {
    type: String,
    required: true,
    enum: ["email", "google"], // Only allow these values
    default: "email",
  },
  createdAt: {
    type: Date,
    default: Date.now,
  },
});

// --- Mongoose Middleware ---

// Hash password before saving (only if it exists and is modified)
userSchema.pre("save", async function (next) {
  // --- CHANGE: Only hash if provider is 'email' and password is set
  if (this.authProvider !== "email" || !this.isModified("password")) {
    return next();
  }
  const salt = await bcrypt.genSalt(10);
  this.password = await bcrypt.hash(this.password, salt);
  next();
});

// Method to compare entered password with hashed password
userSchema.methods.matchPassword = async function (enteredPassword) {
  // This method remains the same, it will only be called for 'email' users
  return await bcrypt.compare(enteredPassword, this.password);
};

module.exports = mongoose.model("User", userSchema);
