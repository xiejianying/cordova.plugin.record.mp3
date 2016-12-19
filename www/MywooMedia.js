var exec = require('cordova/exec');
var cordova = require('cordova');
var channel = require('cordova/channel');


var MyMedia = {

	 
	//开始录音
	startRecord: function(s, e, update , recordEnd) {
		console.log("startRecord");
		//  	MyMedia.fireVoiceEvent();
		exec(s, e, "MediaPlugin", "START_RECORD", []);
		MyMedia.observerRecord(update,recordEnd);

	},
	//结束录音
	stopRecord: function() {
		console.log("stopRecord...");
		exec(null, null, "MediaPlugin", "STOP_RECORD", []);
	},
	//监控回调
	observerRecord: function(updateVoiceFun, recordEnd) {
	 
		var timeout = 100;
		var successCallback = function(msg) {
			console.log("voice update msg status:" + JSON.stringify(msg));
			var status = msg.RECORDSTATUS;
			if (status == 'wait') {
				console.log("wait to recording");
			} else if (status == 'recording') {
				 var voice = msg.VOICE;
				setTimeout(function() {
					if(updateVoiceFun){
						updateVoiceFun.call(updateVoiceFun,msg);
					}
//						cordova.fireDocumentEvent("voice_update", msg);
				}, timeout);
			} else if (status == 'end') {
				var path = msg.RECORDFILE;
				console.log("record end and path:" + path);
				//取消事件监听
				channel.onCordovaReady.unsubscribe(obserFun);
				if(recordEnd){
					recordEnd.call(recordEnd,msg)
				}
				 

			} else {
				//关闭通道
				channel.onCordovaReady.unsubscribe(obserFun);
			}

		};
		var errorCallback = function(e) {
			console.log("Error get voice: " + e);
		};

		//监控回调函数
		function obserFun(){
			exec(successCallback, errorCallback, "MediaPlugin", "VOICE_UPDATE", []);
		}
		//注册监听
		channel.onCordovaReady.subscribe(obserFun);
		
		 

	}
 
};



module.exports = MyMedia;