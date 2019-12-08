# nfcchat10
Proof-of-concept implementing NFC-based message exchange that works on Android 10

I was sad when Android 10 broke NFC. I didn't care about the file-transfer part of "beaming", only the exchange of small bits of data via NDEF. This app demonstrates how to implement that ability on the NFC features that remain in Android 10.

It works by registering the app as a card emulator (to receive messages) and then using "read mode" to connect to an emulated card in order to send messages. The only tricky part comes when both apps have something to send, as being in read mode seems to disable card emulation. My solution is to have a device that has data to send move quickly in and out of read mode for random intervals close to half a second long. That ensures that within a few seconds two devices back-to-back will get to a state where one is listening and the other wanting to send. It seems to work.

There is at least one bug that leads to duplicated messages. I don't care, mostly because a more complicated app using these techniques will prevent duplicate messages elsewhere. My goal here was to write something as simple as possible to explore and document how to do this.

I hope this is useful for somebody. And am happy to take pull requests that preserve the goals.

Enjoy,

--Eric House
