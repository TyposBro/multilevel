// {PATH_TO_PROJECT}/api/services/paymentService.js
const paymeService = require('./providers/paymeService');
// const clickService = require('./providers/clickService'); // Future
// const googlePlayService = require('./providers/googlePlayService'); // Future
const PLANS = require('../config/plans');

/**
 * Initiates a payment process by dispatching to the correct provider service.
 * @param {string} provider - The name of the payment provider (e.g., 'payme').
 * @param {string} planId - The ID of the plan to purchase (e.g., 'gold_monthly').
 * @param {string} userId - The ID of the user.
 * @returns {Promise<object>} The result from the provider service (e.g., { paymentUrl, receiptId }).
 */
const initiatePayment = async (provider, planId, userId) => {
    const plan = PLANS[planId];
    if (!plan) {
        throw new Error('Plan not found');
    }

    switch (provider.toLowerCase()) {
        case 'payme':
            return paymeService.createTransaction(plan, userId);
        
        // case 'click':
        //     return clickService.createTransaction(plan, userId);
        
        // case 'google':
        //     // Google Play is different; it's a verification flow.
        //     throw new Error('Google Play purchases must be verified, not created on the server.');

        default:
            throw new Error('Unsupported payment provider');
    }
};

/**
 * Checks a payment's status by dispatching to the correct provider.
 * @param {string} provider - The name of the payment provider.
 * @param {string} transactionId - The transaction/receipt ID from the provider.
 * @returns {Promise<object>} The status result from the provider.
 */
const checkPaymentStatus = async (provider, transactionId) => {
     switch (provider.toLowerCase()) {
        case 'payme':
            return paymeService.checkTransaction(transactionId);
        
        // case 'click':
        //     return clickService.checkTransaction(transactionId);

        default:
            throw new Error('Unsupported payment provider for status check');
    }
};

module.exports = {
    initiatePayment,
    checkPaymentStatus
};