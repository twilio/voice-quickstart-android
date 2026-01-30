exports.handler = function(context, event, callback) {

    // Use your Conversation Relay URL from .env file or from the event parameters
    const twiml = new Twilio.twiml.VoiceResponse();
    const connect = twiml.connect();
    connect.conversationRelay({
        url: event.ConversationRelayUrl,
        welcomeGreeting: 'Hi! I am an Android S D K voice assistant powered by Twilio and Open A I . Ask me anything!'
    });
    callback(null, twiml);
};