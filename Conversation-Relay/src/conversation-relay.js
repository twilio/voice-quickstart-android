import Fastify from "fastify";
import fastifyWs from "@fastify/websocket";
import OpenAI from "openai";
import dotenv from "dotenv";

// Load environment variables from .env file
dotenv.config();

// globals & constants
const sessions = new Map();
const fastify = Fastify();
const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });

// initialize fastify with websocket support
fastify.register(fastifyWs)
fastify.register(async function (fastify) {
    fastify.get("/conversation-relay", { websocket: true }, (ws, req) => {
        ws.on("message", async (message) => {
            const json_message = JSON.parse(message);
            switch (json_message.type) {
            case "setup":
                console.log("Setup for call:", json_message.callSid);
                ws.callSid = json_message.callSid;
                sessions.set(ws.callSid, []);
                break;
            case "prompt":
                console.log("Processing prompt:", json_message.prompt);
                const conversation = sessions.get(ws.callSid);
                conversation.push({ role: 'user', content: json_message.prompt });

                // send prompt to AI and get response
                const token_stream = await openai.chat.completions.create({
                    model: 'gpt-4o-mini',
                    messages: conversation,
                    stream: true,
                });

                // stream AI response back to client collecting each token as it arrives wo minimize latency
                let collected_tokens = '';
                for await (let chunk of token_stream) {
                    const content = chunk.choices[0].delta.content;
                    if (content) {
                        const partial_response = JSON.stringify({
                            type: 'text',
                            token: content,
                            last: false,
                        });
                        ws.send(partial_response);
                        collected_tokens += content;
                    }
                }
                conversation.push({ role: 'assistant', content: collected_tokens });
                console.log("OpenAI response for call:", json_message.callSid, " ", collected_tokens);
                // send end of response message
                const final_response = JSON.stringify({
                    type: 'text',
                    token: '',
                    last: true,
                });
                ws.send(final_response);
                break;
            case 'interrupt':
                console.log("Handling interruption for call:", json_message.callSid);
                break;
            default:
                console.log("Unknown message type:", json_message.type);
                break;
            }
        });
        ws.on("close", () => {
            console.log("WebSocket connection closed");
            sessions.delete(ws.callSid);
        });
    });
});

// start server
try {
    fastify.listen({ port: process.env.SERVER_PORT ?? 8080 });
    console.log("Conversation Relay server is running.");
} catch (err) {
    fastify.log.error(err);
    process.exit(1);
}
