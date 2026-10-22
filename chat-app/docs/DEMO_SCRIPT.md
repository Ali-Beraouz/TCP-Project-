# 3-minute demo video: shot list

**Before recording:**
- Run `build.sh`.
- Open three terminals in `dist/`.
- Open IntelliJ with the project.
- Increase the editor font size.

Target length: about 3:00.

## Part 1: Source code walkthrough (0:00 – 1:15)

| Time | Show | Say (roughly) |
|---|---|---|
| 0:00 | README title + project tree in IntelliJ | "A multi-client group chat over TCP: two Maven projects, TCPServer and TCPClient, Java 21 and JavaFX." |
| 0:10 | `ChatServer.start()` + `acceptLoop()` | "The server binds a ServerSocket from the port in server.properties, then an accept loop hands every client to its own virtual thread." |
| 0:25 | `ClientHandler.run()` | "Each handler reads JOIN, then MSG, USERS or BYE lines. Writes go through a ReentrantLock, because synchronized would pin virtual threads." |
| 0:40 | `ChatServer.handleMessage()` | "Messages are formatted with the sender and HH:mm time and sent to every connection. READ-ONLY users get an error instead." |
| 0:50 | `ServerListener` + the `model` package imports | "Model–view separation: the model has no JavaFX import and only talks to a listener interface. The JavaFX window and a console view both implement it." |
| 1:05 | `server.properties` + `ClientConfig.resolve()` | "IP and port are read at runtime from properties files; the client also takes them as arguments." |

## Part 2: Live demo (1:15 – 2:50)

| Time | Action | Point out |
|---|---|---|
| 1:15 | `java -jar TCPServer.jar` | Log: *Server Started*, *Waiting for Client...*; Online status; LAN address |
| 1:25 | Client 1: `java -jar TCPClient.jar localhost 3000` → username **alice** | Login screen; server log *Welcome alice*; alice appears in the colour-coded list |
| 1:35 | Client 2 → **bob** | Second coloured row; alice sees "bob joined the chat" |
| 1:45 | alice types a message + **Enter**; bob replies with the **SEND** button | Same message on both clients with sender + time; server log shows CHAT lines |
| 2:00 | Client 3 → leave the username **empty** | READ-ONLY banner, input disabled; server list shows **READ-ONLY** tag; guest still receives messages |
| 2:15 | bob types **allUsers** | Active-users card appears for bob only |
| 2:25 | bob types **bye**; alice clicks **LEAVE** | Indicator turns grey **Offline**; server list shrinks; log shows "left the chat" |
| 2:40 | Edit `server.properties` port → 4000, restart server, connect with `localhost 4000` (optional if time allows) | Config change without recompilation |

## Part 3: Wrap-up (2:50 – 3:00)

Briefly show the class and deployment diagrams in `docs/uml/` and the GitHub repository.
