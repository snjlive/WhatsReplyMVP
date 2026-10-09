import http.client
import json
import os
import threading
import unittest
from unittest.mock import patch
import server

PAYLOAD = {"contact": "Jira", "instruction": "Ответь что оплатим в понедельник", "draft": "",
           "history": [{"kind": "incoming", "text": "Please confirm payment", "time": 10}]}

class ProxyTests(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {"WHATSREPLY_ACCESS_TOKEN": "a" * 32, "OPENAI_API_KEY": "test-provider-key"})
        self.env.start()
        server.Handler.limits = server.Limits()
        self.http = server.ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.worker = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.worker.start()
    def tearDown(self):
        self.http.shutdown(); self.http.server_close(); self.worker.join(); self.env.stop()
    def request(self, payload=PAYLOAD, token="a" * 32, path="/draft"):
        conn = http.client.HTTPConnection("127.0.0.1", self.http.server_port, timeout=3)
        conn.request("POST", path, json.dumps(payload), {"Authorization": "Bearer " + token, "Content-Type": "application/json"})
        response = conn.getresponse()
        status, body = response.status, json.loads(response.read())
        conn.close()
        return status, body
    def test_authentication_rejects_before_calling_provider(self):
        with patch.object(server, "generate") as provider:
            self.assertEqual(self.request(token="wrong")[0], 401)
            provider.assert_not_called()
    def test_generation_and_revision_pass_only_validated_context(self):
        for draft in ("", "We will pay today"):
            payload = {**PAYLOAD, "draft": draft, "unexpected": "ignore me"}
            with patch.object(server, "generate", return_value="We will pay on Monday.") as provider:
                self.assertEqual(self.request(payload), (200, {"draft": "We will pay on Monday."}))
                sent = provider.call_args.args[0]
                self.assertEqual(sent["draft"], draft)
                self.assertNotIn("unexpected", sent)
                self.assertEqual(sent["history"], [{"kind": "incoming", "text": "Please confirm payment"}])
    def test_invalid_history_rejected(self):
        for history in ([], [{"kind": "draft", "text": "x"}], [{"kind": "incoming", "text": "x" * 4001}]):
            with patch.object(server, "generate") as provider:
                self.assertEqual(self.request({**PAYLOAD, "history": history})[0], 400)
                provider.assert_not_called()
    def test_missing_provider_key_is_explicit(self):
        with patch.dict(os.environ, {"OPENAI_API_KEY": ""}):
            self.assertEqual(self.request()[0], 503)
    def test_rate_limit(self):
        with patch.object(server, "generate", return_value="reply"):
            for _ in range(10):
                self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 429)
    def test_provider_error_does_not_leak_credentials(self):
        with patch.object(server, "generate", side_effect=ValueError("test-provider-key")):
            status, body = self.request()
            self.assertEqual(status, 502)
            self.assertNotIn("test-provider-key", json.dumps(body))
    def test_wrong_path(self):
        self.assertEqual(self.request(path="/else")[0], 404)
    def test_provider_response_and_stateless_request(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, *_): pass
            def read(self):
                return json.dumps({"status": "completed", "output": [{"type": "message", "content": [{"type": "output_text", "text": "Thai reply"}]}]}).encode()
        with patch.object(server.urllib.request, "urlopen", return_value=Response()) as request:
            self.assertEqual(server.generate(server.validate(PAYLOAD)), "Thai reply")
            body = json.loads(request.call_args.args[0].data)
            self.assertFalse(body["store"])
            self.assertEqual(body["instructions"], server.SYSTEM)
    def test_daily_limit(self):
        with patch.dict(os.environ, {"DAILY_REQUEST_LIMIT": "1"}), patch.object(server, "generate", return_value="reply"):
            self.assertEqual(self.request()[0], 200)
            self.assertEqual(self.request()[0], 429)

if __name__ == "__main__":
    unittest.main()
