exports.handler = function(context, event, callback) {
  const AccessToken = require('twilio').jwt.AccessToken;
  const VoiceGrant = AccessToken.VoiceGrant;

  const twilioAccountSid = context.ACCOUNT_SID;
  const twilioApiKey = context.API_KEY_SID;
  const twilioApiSecret = context.API_SECRET;

  const outgoingApplicationSid = context.APP_SID;
  const pushCredentialSid = context.PUSH_CREDENTIAL_SID;
  const identity = 'user';

  const voiceGrant = new VoiceGrant({
    outgoingApplicationSid: outgoingApplicationSid,
    pushCredentialSid: pushCredentialSid
  });

  const token = new AccessToken(
    twilioAccountSid,
    twilioApiKey,
    twilioApiSecret,
    { identity }
  );
  token.addGrant(voiceGrant);

  callback(null, token.toJwt());
};
