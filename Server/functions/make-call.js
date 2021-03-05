const callerNumber = '1234567890';
const callerId = 'client:alice';

exports.handler = function(context, event, callback) {
  const twiml = new Twilio.twiml.VoiceResponse();

  var to = event.to;
  if (!to) {
  	twiml.say('Congratulations! You have made your first call! Good bye.');
  } else if (isNumber(to)) {
  	const dial = twiml.dial({callerId : callerNumber});
    dial.number(to);
  } else {
  	const dial = twiml.dial({callerId : callerId});
  	dial.client(to);
  }

  callback(null, twiml);
};

function isNumber(to) {
  if(to.length == 1) {
    if(!isNaN(to)) {
      console.log("It is a 1 digit long number" + to);
      return true;
    }
  } else if(String(to).charAt(0) == '+') {
    number = to.substring(1);
    if(!isNaN(number)) {
      console.log("It is a number " + to);
      return true;
    };
  } else {
    if(!isNaN(to)) {
      console.log("It is a number " + to);
      return true;
    }
  }
  console.log("not a number");
  return false;
}