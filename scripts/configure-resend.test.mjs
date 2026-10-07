import test from 'node:test';
import assert from 'node:assert/strict';
import { resendSettings } from './configure-resend.mjs';

test('Resend settings enable authenticated SMTPS and real delivery', () => {
  const settings = resendSettings(' sender@bank.example ', 're_test_key');
  assert.equal(settings.NOTIFICATION_EMAIL_PROVIDER, 'smtp');
  assert.equal(settings.NOTIFICATION_EMAIL_FROM, 'sender@bank.example');
  assert.equal(settings.SMTP_HOST, 'smtp.resend.com');
  assert.equal(settings.SMTP_PORT, '465');
  assert.equal(settings.SMTP_USERNAME, 'resend');
  assert.equal(settings.SMTP_PASSWORD, 're_test_key');
  assert.equal(settings.SMTP_SSL_ENABLE, 'true');
  assert.equal(settings.SMTP_TEST_CONNECTION, 'true');
  assert.equal(settings.NOTIFICATION_LOG_MESSAGE_CONTENT, 'false');
});

test('Resend settings reject missing credentials', () => {
  assert.throws(() => resendSettings('sender@bank.example', ''), /API key/);
  assert.throws(() => resendSettings('invalid', 're_test_key'), /sender address/);
});
