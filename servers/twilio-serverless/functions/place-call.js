const callerNumber = '1234567890';
const callerId = 'client:alice';
const defaultIdentity = 'alice';

exports.handler = function(context, event, callback) {
  var url = 'https://' + context.DOMAIN_NAME + '/incoming';

  const client = context.getTwilioClient();

  var to =  (event.to) ? event.to : event.To;
  if (!to) {
    client.calls.create({
      url: url,
      to: 'client:' + defaultIdentity,
      from: callerId,
    }, function(err, result) {    
      // End our function
      if (err) {
        callback(err, null);
      } else {
        callback(null, result);
      }
    });
  } else if (isNumber(to)) {
    console.log("Calling number:" + to);
    client.calls.create({
      url: url,
      to: to,
      from: callerNumber,
    }, function(err, result) {    
      // End our function
      if (err) {
        callback(err, null);
      } else {
        callback(null, result);
      }
    });
  } else {
    client.calls.create({
      url: url,
      to: 'client:' + to,
      from: callerId,
    }, function(err, result) {    
      // End our function
      if (err) {
        callback(err, null);
      } else {
        callback(null, result);
      }
    });
  }
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