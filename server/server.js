var logger = require('logging').from(__filename);
var http = require('http');
var dbconn = require('mongoskin').db('localhost:27017/finbert?auto_reconnect');
var argv = require('optimist').argv;
var util = require('util');
var mac = "Wanha, eka, toka, HUUTO, kiroilu, v*ttuilu ja muup*rseily kielletty Seuraus: IP-esto";
var mode = argv.mode;

http.createServer(function (req, res) {
  req.rid = getRequestID();
  var parsedUrl = require('url').parse(req.url, true);
  logger("RID [" + req.rid + "] :: New request");
  logger("RID [" + req.rid + "] :: ", parsedUrl);
  var valid = false;
  if(mode != 'debug') {
      if(messageIsValid(req)) { 
          valid = true;
      }
  } else {
      valid = true;
  }

  if(valid) {
      res.writeHead(200, {'Content-Type': 'text/plain'});
  } else {
      res.writeHead(404, {'Content-Type': 'text/plain'});
      res.write("404 Not Found\n");
      res.end();
      return;
  }

  if(handler[parsedUrl.pathname]) {
     handler[parsedUrl.pathname](res, req);
  } else {
      res.end();
  }
}).listen(4325,"192.168.1.100");

var handler = {
  '/comments/get': function comments_get (res, req) {
     var query = require('url').parse(req.url, true).query;
     logger("RID [" + req.rid + "] :: ", query);
     dbconn.collection('comments').find({ date : query.date }).toArray(function comments_get_callback (err, items){
         logger("RID [" + req.rid + "] :: ", items);
         res.write(JSON.stringify(items));
         res.end();
     })
  }, 

  '/comments/insert': function comments_insert (res, req) {
     var query = require('url').parse(req.url, true).query;
     logger("RID [" + req.rid + "] :: ", query);
     dbconn.collection('comments').insert({ date : query.date, comment : query.comment }, function comments_insert_callback (){
         var result = { success: true };
         logger("RID [" + req.rid + "] :: ", result);
         res.write(JSON.stringify(result));
         res.end();
     })
  },
 
  '/ratings/set': function ratings_set(res, req) {
     var query = require('url').parse(req.url, true);
     logger("RID [" + req.rid + "] :: ", query);
     dbconn.collection('ratings').insert({ date : query.date, rating : query.rating }, function ratings_set_callback(){
         var result = { success: true };
         logger("RID [" + req.rid + "] :: ", result);
         res.write(JSON.stringify(result));
         res.end();
     })
  },
 
  '/ratings/get': function ratings_get (res, req) {
     var query = require('url').parse(req.url, true).query;
     logger("RID [" + req.rid + "] :: ", query);
     dbconn.collection('ratings').find({ date : query.date }).toArray(function ratings_get_callback (err, items){
         logger("RID [" + req.rid + "] :: Calculating rating for", items);
         var sum = 0;
         var isSuccess = false; 
         items.forEach(function(item) {
            sum += item.rating;
            isSuccess = true;
         });
         var result = { success : isSuccess,  rating: sum / items.length };
         logger("RID [" + req.rid + "] :: ", result);
         res.write(JSON.stringify(result));
         res.end();
     })
  } 
}

function messageIsValid (req) {
   var query = require('url').parse(req.url, true).query;
   logger("RID [" + req.rid + "] :: Validating query", query);
   var valid = false;
   var checkSumGenerator = require('crypto').createHash('md5');
   if(query.date && query.checksum) {
       var checkSum = checkSumGenerator.update(query.date + mac).digest('hex');
       if(checkSum === query.checksum) {
          valid = true;
       } else {
          logger("RID [" + req.rid + "] :: Invalid checksum", checkSum, query.checksum);
       }
   } else {
      logger("RID [" + req.rid + "] :: Not enough parameters for query"); 
   } 
   return valid;
}
// between 1000 - 9999
function getRequestID() {
   return Math.floor(Math.random() * 9000) + 1000;
}

logger('Server running at http://192.168.1.100:4325/');