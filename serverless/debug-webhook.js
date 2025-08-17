// debug-webhook.js - Super simple webhook for Click debugging
const http = require('http');

const server = http.createServer((req, res) => {
  console.log('\n=== INCOMING REQUEST ===');
  console.log('Time:', new Date().toISOString());
  console.log('Method:', req.method);
  console.log('URL:', req.url);
  console.log('Headers:', JSON.stringify(req.headers, null, 2));

  let body = '';
  req.on('data', chunk => {
    body += chunk.toString();
  });

  req.on('end', () => {
    console.log('Raw Body:', body);
    
    let data;
    try {
      data = JSON.parse(body);
      console.log('Parsed JSON:', JSON.stringify(data, null, 2));
    } catch (e) {
      console.log('Failed to parse JSON:', e.message);
      data = {};
    }

    // Always respond with success for testing
    const response = {
      error: 0,
      error_note: "Success",
      click_trans_id: data.click_trans_id || 12345,
      merchant_trans_id: data.merchant_trans_id || "test",
      merchant_prepare_id: data.merchant_trans_id || "test",
      merchant_confirm_id: data.merchant_trans_id || "test"
    };

    console.log('Sending Response:', JSON.stringify(response, null, 2));

    res.writeHead(200, { 
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end(JSON.stringify(response));
  });
});

const PORT = 8788;
server.listen(PORT, () => {
  console.log(`🚀 Debug webhook server running on http://localhost:${PORT}`);
  console.log('📝 All requests will be logged and responded to with success');
  console.log('🌐 Use ngrok to expose this: ngrok http ${PORT}');
});

server.on('error', (err) => {
  console.error('Server error:', err);
});
