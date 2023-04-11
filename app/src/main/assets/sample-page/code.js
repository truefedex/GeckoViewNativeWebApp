console.log("Hello from the sample code!");

function sendMessageToNative(message) {
    window.JSBridge.postMessage(message);
}

function webPageCallback(message) {
    document.getElementById("message").innerHTML = message;
}