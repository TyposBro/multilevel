// Simple test server to check if Click can reach us
const http = require('http');

const server = http.createServer((req, res) => {
  console.log('\n=== INCOMING REQUEST ===');
  console.log('Method:', req.method);
  console.log('URL:', req.url);
  console.log('Headers:', req.headers);
  
  let body = '';
  req.on('data', chunk => {
    body += chunk.toString();
  });
  
  req.on('end', () => {
    console.log('Body:', body);
    
    // Respond with a simple success
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      error: 0,
      error_note: 'Success',
      click_trans_id: 12345,
      merchant_trans_id: 'test-123',
      merchant_prepare_id: 'test-123'
    }));
  });
});

server.listen(8788, () => {
  console.log('Test server running on http://localhost:8788');
  console.log('Use ngrok to expose this: ngrok http 8788');
});
