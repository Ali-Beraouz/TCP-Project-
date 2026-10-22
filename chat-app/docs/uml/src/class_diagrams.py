"""Generates the UML class diagrams (Graphviz).  Run:  python3 class_diagrams.py
Writes class-diagram-server.{dot,png,svg} and class-diagram-client.{dot,png,svg} to docs/uml/."""
import html
import pathlib
import subprocess

OUT = pathlib.Path(__file__).resolve().parent.parent
FONT = "Inter"

PACKAGES = {
    # name: (label, background, header colour)
    "launcher": ("launcher", "#FFFBEB", "#FDE68A"),
    "view": ("view  (JavaFX + console)", "#EFF6FF", "#BFDBFE"),
    "model": ("model  (no JavaFX imports)", "#ECFDF5", "#A7F3D0"),
    "config": ("config", "#F8FAFC", "#E2E8F0"),
    "protocol": ("protocol", "#F8FAFC", "#E2E8F0"),
}


def cls(name, package, stereotype=None, attrs=(), methods=()):
    return dict(name=name, package=package, stereotype=stereotype, attrs=attrs, methods=methods)


def label(c):
    head = PACKAGES[c["package"]][2]
    rows = []
    stereo = f'<FONT POINT-SIZE="9">«{html.escape(c["stereotype"])}»</FONT><BR/>' if c["stereotype"] else ""
    italic = c["stereotype"] == "interface"
    name = f"<I>{html.escape(c['name'])}</I>" if italic else html.escape(c["name"])
    rows.append(f'<TR><TD BGCOLOR="{head}" CELLPADDING="6">{stereo}<B>{name}</B></TD></TR>')

    def section(lines):
        if not lines:
            return '<TR><TD CELLPADDING="2"> </TD></TR>'
        body = "<BR ALIGN=\"LEFT\"/>".join(html.escape(l) for l in lines)
        return f'<TR><TD ALIGN="LEFT" BALIGN="LEFT" CELLPADDING="5">{body}<BR ALIGN="LEFT"/></TD></TR>'

    rows.append(section(c["attrs"]))
    rows.append(section(c["methods"]))
    return ('<<TABLE BORDER="1" CELLBORDER="1" CELLSPACING="0" COLOR="#475569" BGCOLOR="white">'
            + "".join(rows) + "</TABLE>>")


EDGE_STYLES = {
    "realizes": 'style=dashed, arrowhead=onormal, arrowsize=1.1',
    "depends": 'style=dashed, arrowhead=vee',
    "assoc": 'arrowhead=vee',
    "composes": 'dir=both, arrowtail=diamond, arrowhead=vee',
    "aggregates": 'dir=both, arrowtail=odiamond, arrowhead=vee',
}


def render(filename, title, classes, edges, extra=""):
    lines = [
        "digraph G {",
        f'  graph [fontname="{FONT}", fontsize=11, rankdir=TB, nodesep=0.45, ranksep=0.65, newrank=true,'
        f' labelloc=t, label=<<B>{html.escape(title)}</B>>, fontsize=16, pad=0.3, bgcolor=white];',
        f'  node [shape=plaintext, fontname="{FONT}", fontsize=10];',
        f'  edge [fontname="{FONT}", fontsize=9, color="#334155", fontcolor="#334155"];',
    ]
    for pkg, (plabel, bg, _) in PACKAGES.items():
        members = [c for c in classes if c["package"] == pkg]
        if not members:
            continue
        lines.append(f'  subgraph cluster_{pkg} {{ label=<<B>{html.escape(plabel)}</B>>; style="rounded,filled";'
                     f' fillcolor="{bg}"; color="#94A3B8"; fontsize=11; margin=14;')
        for c in members:
            lines.append(f'    "{c["name"]}" [label={label(c)}];')
        lines.append("  }")
    for src, dst, kind, text in edges:
        lab = f', label=" {html.escape(text)} "' if text else ""
        if kind == "composes" and text:
            lab = f', headlabel="{text}", labeldistance=2.0'
        lines.append(f'  "{src}" -> "{dst}" [{EDGE_STYLES[kind]}{lab}];')
    lines.append(extra)
    lines.append("}")
    dot = "\n".join(lines)
    (OUT / f"{filename}.dot").write_text(dot)
    for fmt in ("png", "svg"):
        args = ["dot", f"-T{fmt}", "-o", str(OUT / f"{filename}.{fmt}"), str(OUT / f"{filename}.dot")]
        if fmt == "png":
            args.insert(1, "-Gdpi=130")
        subprocess.run(args, check=True)


# ------------------------------------------------------------------ server
server_classes = [
    cls("TCPServer", "launcher", "main class",
        [], ["+ main(args: String[])", "- runConsole()"]),
    cls("ServerApp", "view", "JavaFX Application",
        ["- server: ChatServer", "- view: ServerView"],
        ["+ start(stage: Stage)", "+ stop()", "- toggleServer()", "- startServer()"]),
    cls("ServerView", "view", None,
        ["- root: GridPane", "- users: ObservableList<ConnectedUser>", "- logEntries: ObservableList<LogEntry>",
         "- statusDot: Circle"],
        ["+ showStarted(host, port, lanAddresses)", "+ showStopped()", "+ addUser(user: ConnectedUser)",
         "+ removeUser(user: ConnectedUser)", "+ appendLog(entry: LogEntry)", "+ messageRelayed()",
         "+ setOnToggle(action: Runnable)"]),
    cls("FxServerListener", "view", None,
        ["- view: ServerView"],
        ["+ onLog(entry)  // Platform.runLater", "+ onUserJoined(user)", "+ onUserLeft(user)",
         "+ onServerStarted(host, port)", "+ onServerStopped()"]),
    cls("ConsoleServerView", "view", None, [], ["+ onLog(entry: LogEntry)"]),
    cls("ChatServer", "model", None,
        ["- config: ServerConfig", "- listener: ServerListener",
         "- clients: Map<String, ClientHandler>", "- workers: ExecutorService  // virtual threads",
         "- serverSocket: ServerSocket", "- palette: ColorPalette", "- running: boolean"],
        ["+ start()", "+ stop()", "+ isRunning(): boolean", "+ getPort(): int",
         "+ getActiveUsers(): List<ConnectedUser>", "~ join(h: ClientHandler, name: String): ConnectedUser",
         "~ handleMessage(h: ClientHandler, text: String)", "~ sendUserList(h: ClientHandler)",
         "~ disconnected(h: ClientHandler, byChoice: boolean)", "- broadcast(line: String)"]),
    cls("ClientHandler", "model", "Runnable",
        ["- socket: Socket", "- out: BufferedWriter", "- writeLock: ReentrantLock", "- user: ConnectedUser"],
        ["+ run()", "~ send(line: String)", "~ welcome(user, line)", "~ requestClose()", "~ close()"]),
    cls("ServerListener", "model", "interface",
        [], ["+ onLog(entry: LogEntry)", "+ onServerStarted(host, port)", "+ onServerStopped()",
             "+ onUserJoined(user: ConnectedUser)", "+ onUserLeft(user: ConnectedUser)",
             "+ onMessageRelayed(sender, text)"]),
    cls("ConnectedUser", "model", "record",
        ["name: String", "color: String", "readOnly: boolean", "address: String", "joinedAt: LocalTime"], []),
    cls("LogEntry", "model", "record",
        ["time: LocalTime", "level: Level", "message: String"], ["+ of(level, message): LogEntry"]),
    cls("ColorPalette", "model", None, ["- hue: AtomicReference<Double>"], ["+ next(): String  // #RRGGBB"]),
    cls("NetworkInfo", "model", "utility", [], ["+ lanAddresses(): List<String>"]),
    cls("ServerConfig", "config", "record",
        ["host: String", "port: int", "source: String"],
        ["+ load(): ServerConfig", "+ fromFile(file: Path)", "+ fromProperties(p, source)"]),
    cls("Protocol", "protocol", "utility",
        ["+ JOIN, MSG, USERS, BYE", "+ WELCOME, CHAT, USERLIST, INFO, ERROR"],
        ["+ encode(type, fields...): String", "+ escape(text) / unescape(text)",
         "+ isValidUsername(name): boolean", "+ sanitizeUsername(name): String"]),
    cls("Packet", "protocol", "record",
        ["type: String", "payload: String"], ["+ parse(line): Packet", "+ fields(count): String[]"]),
]
server_edges = [
    ("TCPServer", "ServerApp", "depends", "launches"),
    ("TCPServer", "ConsoleServerView", "depends", "--console"),
    ("ServerApp", "ServerView", "composes", ""),
    ("ServerApp", "ChatServer", "composes", ""),
    ("ServerApp", "FxServerListener", "depends", "creates"),
    ("FxServerListener", "ServerView", "assoc", "updates"),
    ("FxServerListener", "ServerListener", "realizes", ""),
    ("ConsoleServerView", "ServerListener", "realizes", ""),
    ("ChatServer", "ServerListener", "assoc", "notifies"),
    ("ChatServer", "ClientHandler", "composes", "0..*"),
    ("ChatServer", "ColorPalette", "composes", ""),
    ("ChatServer", "ServerConfig", "assoc", ""),
    ("ClientHandler", "ConnectedUser", "assoc", "0..1"),
    ("ChatServer", "LogEntry", "depends", "creates"),
    ("ClientHandler", "Packet", "depends", "parses"),
    ("ChatServer", "Protocol", "depends", "encodes"),
    ("FxServerListener", "NetworkInfo", "depends", ""),
]

# ------------------------------------------------------------------ client
client_classes = [
    cls("TCPClient", "launcher", "main class",
        [], ["+ main(args: String[])  // <ServerIP> <Port>"]),
    cls("ClientApp", "view", "JavaFX Application",
        ["- controller: ClientController"], ["+ start(stage: Stage)", "+ stop()"]),
    cls("ClientController", "view", None,
        ["- config: ClientConfig", "- client: ChatClient", "- loginView: LoginView", "- chatView: ChatView"],
        ["+ show()", "+ shutdown()", "- join(name: String)", "- send(text: String)", "- leaveOrClose()",
         "+ onWelcome(...) / onMessage(...)", "+ onUserList(...) / onDisconnected(...)"]),
    cls("LoginView", "view", None,
        ["- root: GridPane", "- usernameField: TextField", "- joinButton: Button"],
        ["+ setOnJoin(handler: Consumer<String>)", "+ setBusy(busy: boolean)", "+ showError(message)"]),
    cls("ChatView", "view", None,
        ["- root: GridPane", "- messages: VBox", "- input: TextArea", "- sendButton / leaveButton: Button",
         "- statusDot: Circle", "- statusLabel: Label  // Online"],
        ["+ addMessage(m: ChatMessage)", "+ addUserList(users, readOnlyCount)", "+ addInfo(text) / addError(text)",
         "+ showOffline(reason)", "+ setOnSend / setOnLeave / setOnShowUsers"]),
    cls("FxClientListener", "view", "decorator",
        ["- delegate: ClientListener"], ["+ onXxx(...)  // Platform.runLater(delegate::onXxx)"]),
    cls("ConsoleClient", "view", None,
        ["- client: ChatClient"], ["+ run()", "+ onMessage(m) ..."]),
    cls("ChatClient", "model", None,
        ["- socket: Socket", "- out: BufferedWriter", "- listener: ClientListener", "- readOnly: boolean",
         "- username: String", "- connected: boolean"],
        ["+ connect(host, port, username)", "+ send(text): SendResult", "+ requestUserList()", "+ leave()",
         "+ isConnected(): boolean", "- readLoop(in)  // virtual thread", "- dispatch(p: Packet)"]),
    cls("SendResult", "model", "enumeration",
        ["SENT", "COMMAND", "EMPTY", "BLOCKED_READ_ONLY", "NOT_CONNECTED"], []),
    cls("ClientListener", "model", "interface",
        [], ["+ onWelcome(username, readOnly, color)", "+ onMessage(m: ChatMessage)",
             "+ onUserList(users, readOnlyCount)", "+ onInfo(text)", "+ onError(text)",
             "+ onDisconnected(reason)"]),
    cls("ChatMessage", "model", "record",
        ["time: String", "sender: String", "color: String", "text: String", "mine: boolean"], []),
    cls("ClientConfig", "config", "record",
        ["host: String", "port: int", "source: String"],
        ["+ resolve(args): ClientConfig  // CLI args, else file", "+ load(): ClientConfig"]),
    cls("Protocol", "protocol", "utility",
        ["+ JOIN, MSG, USERS, BYE", "+ WELCOME, CHAT, USERLIST, INFO, ERROR"],
        ["+ encode(type, fields...): String", "+ escape(text) / unescape(text)"]),
    cls("Packet", "protocol", "record",
        ["type: String", "payload: String"], ["+ parse(line): Packet", "+ fields(count): String[]"]),
]
client_edges = [
    ("TCPClient", "ClientConfig", "depends", "validates args"),
    ("TCPClient", "ClientApp", "depends", "launches"),
    ("TCPClient", "ConsoleClient", "depends", "--console"),
    ("ClientApp", "ClientController", "composes", ""),
    ("ClientController", "LoginView", "composes", ""),
    ("ClientController", "ChatView", "composes", ""),
    ("ClientController", "ChatClient", "composes", ""),
    ("ClientController", "ClientListener", "realizes", ""),
    ("ClientController", "FxClientListener", "depends", "wraps itself"),
    ("FxClientListener", "ClientListener", "realizes", ""),
    ("ConsoleClient", "ClientListener", "realizes", ""),
    ("ConsoleClient", "ChatClient", "composes", ""),
    ("ChatClient", "ClientListener", "assoc", "notifies"),
    ("ChatClient", "SendResult", "depends", "returns"),
    ("ChatClient", "ChatMessage", "depends", "creates"),
    ("ChatClient", "Packet", "depends", "parses"),
    ("ChatClient", "Protocol", "depends", "encodes"),
    ("ChatView", "ChatMessage", "depends", "renders"),
]

if __name__ == "__main__":
    render("class-diagram-server", "TCPServer - Class Diagram", server_classes, server_edges)
    render("class-diagram-client", "TCPClient - Class Diagram", client_classes, client_edges)
    print("class diagrams written to", OUT)
