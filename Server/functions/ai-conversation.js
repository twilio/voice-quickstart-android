exports.handler = function(context, event, callback) {
    const CONVERSATION_RELAY_URL = context.CONVERSATION_RELAY_URL || event.ConversationRelayUrl;

    // Use your Conversation Relay URL from .env file or from the event parameters
    const twiml = new Twilio.twiml.VoiceResponse();
    const connect = twiml.connect();
    connect.conversationRelay({
        url: `wss://${CONVERSATION_RELAY_URL}/conversation-relay`,
        welcomeGreeting: 'Hi! I am a Javascript S D K voice assistant powered by Twilio and Open A I . Ask me anything!'
    });
    callback(null, twiml);
};