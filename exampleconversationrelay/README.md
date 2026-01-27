# Conversation Relay Example & Architecture

## Table of Contents

1. [Introduction](#introduction)  
   1.1. [Purpose](#purpose-of-this-document)  
   1.2. [Document structure](#document-structure)  
2. [The Demo](#the-demo)  
   2.1. [Deploying the Twilio Serverless demo](#deploying-the-twilio-serverless-demo)  
   2.2. [Setting up and running the conversation-relay server](#setting-up-and-running-the-conversation-relay-server)  
   2.3. [Running the Android client](#running-the-android-client)  
3. [How Conversation Relay Works](#how-conversation-relay-works)  
   3.1. [High-level overview](#high-level-overview)  
   3.2. [In-depth message flow](#in-depth-message-flow)  

---

## 1. Introduction

### 1.1. Purpose

This document describes an example implementation of Twilio Conversation Relay. It walks through how to deploy and run a demo that ties together Twilio Voice, a Node.js WebSocket server, and an LLM, and then explains how Conversation Relay works under the hood.

### 1.2. Document structure

The document is split into two main parts:

1. **The Demo**  
   How to:  
   \- Deploy the Twilio Serverless components using `twilio-cli`.  
   \- Set up and run the `conversation-relay` Node.js WebSocket server.  
   \- Configure the Android client to connect to the Conversation Relay endpoint.  

2. **How Conversation Relay Works**  
   A conceptual and technical explanation of:  
   \- What the `<ConversationRelay>` TwiML tag does.  
   \- How messages are exchanged between Twilio and your WebSocket endpoint.  
   \- How those messages can be used to integrate with an LLM or other backends.

## 2. The Demo

### 2.1. Deploying the Twilio Serverless demo

The first part of the demo uses Twilio Serverless functions and assets to provide a TwiML application for your client.

1. Use the `twilio-cli` tool to publish the Serverless demo from the folder `servers/twilio-serverless`.
2. Follow the instructions in the project README [here](https://github.com/twilio/voice-quickstart-android?tab=readme-ov-file#3-use-twilio-cli-to-deploy-access-token-and-twiml-application-to-twilio-serverless)

That README explains how to:

\- Install and configure `twilio-cli`.  
\- Deploy the Serverless project.  
\- Retrieve URLs and credentials needed by the Android client.

### 2.2. Setting up and running the conversation-relay server

This section describes how to run the WebSocket server implemented in `servers/conversation-relay`.

#### 2.2.1. Install ngrok and run ngrok

Install ngrok on your machine. On macOS, a common approach is:

```bash
brew install ngrok
```
Or download it directly from the ngrok website and follow their installation instructions.

Run ngrok to expose your local Conversation Relay server to the public internet. For example, if you plan to run the server on port 8080:

```bash
ngrok http <your desired port, e.g., 8080>
```

#### 2.2.2. Install Node.js 22+

Ensure Node.js v22 or later is installed. On macOS, for example:

```bash
brew install node@22
```
or use a version manager such as nvm:
```bash
nvm install 22
nvm use 22
```

#### 2.2.3. Set up the .env file

From the project root, create your environment file for conversation-relay:

1. Copy the example file:
```bash
cp servers/conversation-relay/.env.example servers/conversation-relay/.env
```
2. Edit servers/conversation-relay/.env and set the following variables:
   - `SERVER_LOCAL_PORT`: The local port where the Conversation Relay server will listen (for example, 8080).
   - `SERVER_PUBLIC_URL`: The public WebSocket URL exposed by ngrok. Should be in the form of `wss://<hostname-from-ngrok>/conversation-relay`.
   - `TWILIO_AUTH_TOKEN`: Your Twilio Auth Token, available in the Twilio Console.
   - `OPENAI_API_KEY` : Your OpenAI API key, retrievable it from your OpenAI account dashboard.

#### 2.2.4. Install dependencies and run the server
From the `servers/conversation-relay` folder, install the dependencies and start the server:

1. Install dependencies:
```bash
cd servers/conversation-relay
npm install
```
2. Start the server:
```bash
npm start
```

The server will start listening on `SERVER_LOCAL_PORT` and will be reachable publicly via the ngrok URL configured in `SERVER_PUBLIC_URL`.

#### 2.2.5. Creating the TwiML application

Next, we need to create a TwiML application. A TwiML application identifies a public URL for retrieving [TwiML call control instructions](https://www.twilio.com/docs/voice/twiml). When your QS app makes a call to the Twilio cloud, Twilio will make a webhook request to this URL, your application server will respond with generated TwiML, and Twilio will execute the instructions you’ve provided.

Use Twilio CLI to create a TwiML app with the `ai-conversation` endpoint you have just deployed (**Note: replace the value of `--voice-url` parameter with your `ai-conversation` endpoint you just deployed to Twilio Serverless** in step 2.1)

    $ twilio api:core:applications:create \
        --friendly-name=quickstart-conversation-relay \
        --voice-method=POST \
        --voice-url="https://<my quickstart url>/ai-conversation"

You should receive an Appliciation SID that looks like this

    APxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

### 2.3. Running the Android client

To run the Android client with Conversation Relay support, configure two variables in the main activity of the Android app:

1. Access Token : In the Android project, open `ConversationRelayActivity.java` and set the `accessToken` variable to a valid Twilio Access Token. This token is usually provided by using the twilio-cli tool to generate a token. When generating the token, make sure to use the application SID created in step 2.2.5. More information on how to do this can be found [here](https://github.com/twilio/voice-quickstart-android?tab=readme-ov-file#bullet5)
2. Conversation Relay URL: In the same file (`ConversationRelayActivity.java`), set the `conversationRelayUrl` variable to the same public server URL used in the .env file, including the WebSocket path. For example:
   `wss://abc123.ngrok.app/conversation-relay`

Once those two values are in place, build and run the Android app. When a call is established and the `<ConversationRelay>` TwiML is invoked, the client and server will communicate via the Conversation Relay WebSocket endpoint.

## 3. How Conversation Relay works

### 3.1. High-level overview

Twilio Conversation Relay is powered by the `<ConversationRelay>` [TwiML tag](https://www.twilio.com/docs/voice/twiml/connect/conversationrelay)

At a high level:
1. Twilio handles the voice call and transcription. 
2. The `<ConversationRelay>` tag instructs Twilio to connect to your WebSocket endpoint. 
3. As the caller speaks, Twilio:
    * Transcribes the speech to text.
    * Sends the transcription and related events to your WebSocket endpoint as JSON messages. 
4. Your WebSocket server can:
    * Forward the transcribed text to an LLM (as in this demo) or any other backend. 
    * Process the result and send text responses back over the WebSocket.
5. Twilio converts the text responses back to speech and plays them to the caller in real time.

  For a longer walkthrough, see this example [article](https://www.twilio.com/en-us/blog/native-integration-conversational-intelligence-conversationrelay-node)

### 3.2. In-depth message flow

Once the WebSocket is established between Twilio and your Conversation Relay server, Twilio sends JSON messages and expects the your endpoint to handle specific message types. The demo server in `servers/conversation-relay` handles the following:

1. **setup**
    * The initial message sent when the Conversation Relay session starts.
    * Contains the call SID and related metadata about the caller and session.
    * The server typically uses this to initialize any conversation state, such as setting up a system prompt for the LLM.

2. **prompt**
    * Contains a field such as `voicePrompt` with the transcribed text of what the caller said.
    * The server uses this payload to:
      - Append the user message to the in-memory conversation state.
      - Call out to an LLM or other backend using this text.
      - Stream or send back a text response, which Twilio then converts to speech for the caller.
3. **interrupt**
    * Sent when the caller interrupts the current response (for example, by speaking while Twilio is playing back a response).
    * Indicates that the current TTS playback should be stopped and that the server should adjust its behavior accordingly (for example, by stopping any ongoing streaming response and preparing for the user’s next utterance).

By combining these message types, the Conversation Relay server can maintain a real-time, bidirectional conversation: capturing user speech as transcribed text, sending it to an LLM, and streaming LLM responses back to the caller as synthesized speech.
