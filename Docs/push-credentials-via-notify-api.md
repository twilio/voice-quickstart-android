## Create Push Credentials via the Notify Credential Resource API

Voice SDK users can manage their Push Credentials in the developer console (**Console > Account > Keys & Credentials > Credentials**). Currently the Push Credential management page only supports the default region (US1). To create or update Push Credentials for other regional (i.e. Australia) usage, developers can use the Notify public API to manage their Push Credentials. Follow the instructions of the [Credential Resource API](https://www.twilio.com/docs/notify/api/credential-resource) and replace the endpoint with the regional endpoint, for example https://notify.sydney.au1.twilio.com for the Australia region.

You will also need:
- FCM key: follow the [instructions]((https://github.com/twilio/voice-quickstart-android#1-generate-google-servicesjson)) to get the FCM key.
- Twilio account credentials: find your API auth token for the specific region in the developer console. Go to **Console > Account > Keys & Credentials > API keys & tokens** and select the region in the dropdown menu.

Example of creating an `AU1` Push Credential for FCM:

```
curl -X POST https://notify.sydney.au1.twilio.com/v1/Credentials \
--data-urlencode "Type=fcm" \
--data-urlencode "Secret=$FCM_SERVER_KEY" \
-u $TWILIO_ACCOUNT_SID:$TWILIO_AUTH_TOKEN
```

To update a Push Credential (CR****) in `AU1`:

```
curl -X POST https://notify.sydney.au1.twilio.com/v1/Credentials/CR**** \
--data-urlencode "Type=fcm" \
--data-urlencode "Secret=$FCM_SERVER_KEY" \
-u $TWILIO_ACCOUNT_SID:$TWILIO_AUTH_TOKEN
```
