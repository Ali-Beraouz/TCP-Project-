"""Generates the deployment, sequence and use case diagrams (matplotlib).  Run:  python3 other_diagrams.py"""
import pathlib

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Ellipse, FancyBboxPatch, Polygon, Rectangle

OUT = pathlib.Path(__file__).resolve().parent.parent
plt.rcParams["font.family"] = ["Inter", "DejaVu Sans"]
INK = "#1F2937"
MUTED = "#64748B"


def save(fig, name):
    for ext in ("png", "svg"):
        fig.savefig(OUT / f"{name}.{ext}", dpi=170, bbox_inches="tight", facecolor="white")
    plt.close(fig)


# ===================================================================== deployment
def node3d(ax, x, y, w, h, title, fill, depth=0.35):
    """UML node: a 3D box. (x, y) is the bottom-left of the front face."""
    ax.add_patch(Polygon([[x, y + h], [x + depth, y + h + depth], [x + w + depth, y + h + depth], [x + w, y + h]],
                         closed=True, fc=fill, ec=INK, lw=1.3))
    ax.add_patch(Polygon([[x + w, y], [x + w + depth, y + depth], [x + w + depth, y + h + depth], [x + w, y + h]],
                         closed=True, fc=fill, ec=INK, lw=1.3))
    ax.add_patch(Rectangle((x, y), w, h, fc="white", ec=INK, lw=1.3))
    ax.text(x + w / 2, y + h - 0.32, title, ha="center", va="top", fontsize=10.5, color=INK, linespacing=1.4)


def box(ax, x, y, w, h, text, fill, ec=INK, fs=9, dashed=False, weight="normal"):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=0.12", fc=fill, ec=ec, lw=1.1,
                                ls="--" if dashed else "-"))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=fs, color=INK, linespacing=1.35,
            weight=weight)


def artifact(ax, x, y, w, h, title, sub):
    box(ax, x, y, w, h, "", "#FFFBEB", ec="#B45309")
    ax.text(x + 0.18, y + h - 0.2, "«artifact»", fontsize=7.5, color="#92400E", va="top")
    ax.text(x + 0.18, y + h - 0.48, title, fontsize=10, color=INK, va="top", weight="bold")
    ax.text(x + 0.18, y + 0.18, sub, fontsize=7.8, color=MUTED, va="bottom", linespacing=1.3)
    # little document icon
    ix, iy = x + w - 0.45, y + h - 0.55
    ax.add_patch(Polygon([[ix, iy], [ix, iy + 0.38], [ix + 0.2, iy + 0.38], [ix + 0.3, iy + 0.28], [ix + 0.3, iy]],
                         closed=True, fc="white", ec="#B45309", lw=0.9))


def deployment():
    fig, ax = plt.subplots(figsize=(15, 10.4))
    ax.set_xlim(0, 30)
    ax.set_ylim(-3.4, 17.2)
    ax.axis("off")
    ax.text(15, 16.8, "TCP Chat - Deployment Diagram", ha="center", fontsize=15, weight="bold", color=INK)

    # server node
    node3d(ax, 0.6, 3.2, 11.4, 11.2, "«device»\nServer machine", "#DBEAFE")
    box(ax, 1.2, 3.8, 10.2, 8.6, "", "#EFF6FF", ec="#2563EB")
    ax.text(1.45, 12.15, "«execution environment»  JVM 21  (virtual threads)", fontsize=8.8, color="#1E40AF", va="top")
    artifact(ax, 1.7, 9.2, 9.2, 2.2, "TCPServer.jar",
             "ChatServer (model) + JavaFX ServerView\nmain: com.chatapp.server.TCPServer")
    artifact(ax, 1.7, 6.6, 9.2, 2.1, "server.properties",
             "server.host=0.0.0.0\nserver.port=3000   (read at runtime)")
    box(ax, 1.7, 4.3, 9.2, 1.8, "ServerSocket bound to 0.0.0.0:3000\naccept() loop  →  1 ClientHandler per client",
        "white", ec="#94A3B8", fs=8.8, dashed=True)

    # client nodes
    ys = [11.2, 6.3, 1.4]
    labels = ["Client PC 1", "Client PC 2", "Client PC n"]
    for y, lab in zip(ys, labels):
        node3d(ax, 18.2, y, 10.6, 4.1, f"«device»  {lab}", "#DCFCE7")
        box(ax, 18.7, y + 0.35, 9.6, 2.45, "", "#F0FDF4", ec="#16A34A")
        ax.text(18.95, y + 2.62, "«execution environment»  JVM 21", fontsize=8.3, color="#166534", va="top")
        artifact(ax, 19.0, y + 0.55, 5.4, 1.65, "TCPClient.jar", "JavaFX ChatView")
        artifact(ax, 24.7, y + 0.55, 3.3, 1.65, "client.prop.", "optional")
    ax.text(23.5, 5.95, "⋮", fontsize=18, ha="center", color=MUTED)

    # communication paths
    for y, lab in zip(ys, labels):
        ty = y + 2.05
        ax.annotate("", xy=(18.2, ty), xytext=(12.0, 8.8),
                    arrowprops=dict(arrowstyle="-", lw=1.6, color="#0F172A"))
        mx, my = (18.2 + 12.0) / 2, (ty + 8.8) / 2
        ax.text(mx, my + 0.25, "«TCP/IP»", ha="center", fontsize=8.5, color="#0F172A", weight="bold",
                bbox=dict(boxstyle="round,pad=0.2", fc="white", ec="none"))
    ax.text(15.1, -0.4, "Communication path: TCP socket on port 3000 (LAN or localhost)\n"
                        "Line-based UTF-8 protocol:  JOIN · MSG · USERS · BYE  /  WELCOME · CHAT · USERLIST · INFO · ERROR\n"
                        "Client start-up:  java -jar TCPClient.jar <ServerIPAddress> 3000",
            ha="center", va="top", fontsize=8.8, color=INK, linespacing=1.5,
            bbox=dict(boxstyle="round,pad=0.5", fc="#F8FAFC", ec="#CBD5E1"))
    save(fig, "deployment-diagram")


# ===================================================================== sequence
def sequence():
    lifelines = [
        ("Alice", "actor", "#FFFFFF"),
        ("Client 1\n(ChatView + ChatClient)", "obj", "#DCFCE7"),
        ("ClientHandler\n(virtual thread)", "obj", "#DBEAFE"),
        ("ChatServer", "obj", "#DBEAFE"),
        ("Client 2..n\n(other users)", "obj", "#DCFCE7"),
    ]
    xs = [1.4, 5.6, 10.4, 14.8, 19.2]
    steps = [
        ("frag", "Join"),
        (0, 1, "enter username, click Join", "sync"),
        (1, 2, "TCP connect  +  JOIN|alice", "sync"),
        (2, 3, "join(handler, \"alice\")", "sync"),
        (3, 2, "welcome(user)  // sends WELCOME", "reply"),
        (2, 1, "WELCOME|alice|FULL|#A7F3D0", "reply"),
        (3, 4, "INFO|alice joined the chat", "async"),
        ("frag", "Send a message (Enter or SEND)"),
        (0, 1, "type \"Salam!\" + Enter", "sync"),
        (1, 2, "MSG|Salam!", "async"),
        (2, 3, "handleMessage(handler, \"Salam!\")", "sync"),
        (3, 3, "format with sender + time (HH:mm)", "self"),
        (3, 1, "CHAT|14:05|alice|#A7F3D0|Salam!", "async"),
        (3, 4, "CHAT|14:05|alice|#A7F3D0|Salam!", "async"),
        ("frag", "allUsers command"),
        (0, 1, "type \"allUsers\"", "sync"),
        (1, 2, "USERS", "async"),
        (2, 3, "sendUserList(handler)", "sync"),
        (2, 1, "USERLIST|alice,bob|1   (requester only)", "reply"),
        ("frag", "Leave (LEAVE button, \"end\" or \"bye\")"),
        (0, 1, "click LEAVE", "sync"),
        (1, 2, "BYE  (then shutdownOutput)", "async"),
        (2, 3, "disconnected(handler, byChoice=true)", "sync"),
        (3, 4, "INFO|alice left the chat", "async"),
        (2, 1, "connection closed  →  status: Offline", "reply"),
    ]
    step_h = 0.62
    n_rows = len(steps)
    top = n_rows * step_h + 1.8
    fig, ax = plt.subplots(figsize=(15, top * 0.55))
    ax.set_xlim(0, 21.2)
    ax.set_ylim(-0.3, top + 1.3)
    ax.axis("off")
    ax.text(10.6, top + 1.0, "TCP Chat - Sequence Diagram (join, message, allUsers, leave)", ha="center",
            fontsize=14, weight="bold", color=INK)

    head_y = top - 0.2
    for x, (name, kind, fill) in zip(xs, lifelines):
        if kind == "actor":
            hy = head_y + 0.15
            ax.add_patch(plt.Circle((x, hy + 0.55), 0.17, fc="white", ec=INK, lw=1.2))
            ax.plot([x, x], [hy + 0.38, hy - 0.05], color=INK, lw=1.2)
            ax.plot([x - 0.3, x + 0.3], [hy + 0.25, hy + 0.25], color=INK, lw=1.2)
            ax.plot([x, x - 0.25], [hy - 0.05, hy - 0.35], color=INK, lw=1.2)
            ax.plot([x, x + 0.25], [hy - 0.05, hy - 0.35], color=INK, lw=1.2)
            ax.text(x, hy - 0.5, name, ha="center", va="top", fontsize=9.5, weight="bold", color=INK)
        else:
            box(ax, x - 1.75, head_y - 0.45, 3.5, 1.05, name, fill, fs=8.8, weight="bold")
        ax.plot([x, x], [head_y - 0.5 if kind != "actor" else head_y - 0.65, 0.1], color="#94A3B8", lw=1,
                ls=(0, (4, 3)), zorder=0) if False else ax.plot([x, x], [head_y - 0.5, 0.55], color="#94A3B8", lw=1, ls=(0, (4, 3)), zorder=0)

    y = head_y - 1.15
    for step in steps:
        if step[0] == "frag":
            ax.add_patch(Rectangle((0.3, y - 0.18), 20.6, 0.42, fc="#F1F5F9", ec="#CBD5E1", lw=0.8, zorder=0))
            ax.text(0.45, y + 0.03, step[1], fontsize=8.8, weight="bold", color="#334155", va="center")
            y -= step_h
            continue
        a, b, text, kind = step
        if kind == "self":
            x0 = xs[a]
            ax.annotate("", xy=(x0 + 0.08, y - 0.22), xytext=(x0 + 0.08, y + 0.08),
                        arrowprops=dict(arrowstyle="-|>", color=INK, lw=1.1,
                                        connectionstyle="arc3,rad=-1.6"))
            ax.text(x0 + 0.6, y - 0.07, text, fontsize=8.4, color=INK, va="center")
        else:
            x0, x1 = xs[a], xs[b]
            style = dict(arrowstyle="-|>" if kind == "sync" else "->", color=INK, lw=1.15,
                         ls="--" if kind == "reply" else "-")
            ax.annotate("", xy=(x1, y), xytext=(x0, y), arrowprops=style)
            ax.text((x0 + x1) / 2, y + 0.06, text, ha="center", va="bottom", fontsize=8.3, color=INK)
        y -= step_h

    ax.text(0.3, -0.05, "solid arrow = call / message      dashed arrow = reply      "
                      "Every server→client arrow is one line on the TCP socket.", fontsize=8, color=MUTED)
    save(fig, "sequence-diagram")


# ===================================================================== use case
def actor(ax, x, y, name):
    ax.add_patch(plt.Circle((x, y + 0.75), 0.24, fc="white", ec=INK, lw=1.4))
    ax.plot([x, x], [y + 0.51, y - 0.1], color=INK, lw=1.4)
    ax.plot([x - 0.4, x + 0.4], [y + 0.3, y + 0.3], color=INK, lw=1.4)
    ax.plot([x, x - 0.35], [y - 0.1, y - 0.6], color=INK, lw=1.4)
    ax.plot([x, x + 0.35], [y - 0.1, y - 0.6], color=INK, lw=1.4)
    ax.text(x, y - 0.8, name, ha="center", va="top", fontsize=9.5, weight="bold", color=INK, linespacing=1.3)


def usecase(ax, x, y, text, fill="#EEF2FF"):
    ax.add_patch(Ellipse((x, y), 4.9, 1.2, fc=fill, ec="#4338CA", lw=1.2))
    ax.text(x, y, text, ha="center", va="center", fontsize=8.8, color=INK, linespacing=1.25)


def use_cases():
    fig, ax = plt.subplots(figsize=(14, 9))
    ax.set_xlim(0, 28)
    ax.set_ylim(0, 18)
    ax.axis("off")
    ax.text(14, 17.5, "TCP Chat - Use Case Diagram", ha="center", fontsize=15, weight="bold", color=INK)

    ax.add_patch(FancyBboxPatch((5.2, 0.6), 17.4, 16.0, boxstyle="round,pad=0,rounding_size=0.3",
                                fc="#FAFAFA", ec="#94A3B8", lw=1.2))
    ax.text(13.9, 16.2, "Multi-Client Chat System", ha="center", fontsize=11, weight="bold", color="#334155")

    uc = {
        "connect": (10.0, 14.6, "Connect to server\n(IP + port)"),
        "join": (10.0, 12.6, "Join with a username"),
        "readonly": (10.0, 10.6, "Join in READ-ONLY mode\n(no username)"),
        "receive": (10.0, 8.6, "Receive group messages\n(sender + time)"),
        "send": (10.0, 6.6, "Send message\n(SEND button / Enter)"),
        "users": (10.0, 4.6, "List active users\n(\"allUsers\")"),
        "leave": (10.0, 2.6, "Leave chat\n(LEAVE / \"end\" / \"bye\")"),
        "start": (18.4, 13.4, "Start / stop server"),
        "monitor": (18.4, 10.8, "Monitor connected users\n(colour-coded list)"),
        "log": (18.4, 8.2, "View activity log"),
        "config": (18.4, 5.6, "Configure IP / port\n(.properties file)"),
    }
    for key, (x, y, t) in uc.items():
        usecase(ax, x, y, t, "#ECFDF5" if x > 15 else "#EEF2FF")

    actor(ax, 2.3, 10.6, "Chat user")
    actor(ax, 2.3, 3.6, "Read-only\nuser")
    actor(ax, 25.6, 9.8, "Server\noperator")

    def line(p, key, side="left"):
        x, y, _ = uc[key]
        ax.plot([p[0], x - 2.3 if side == "left" else x + 2.3], [p[1], y], color=INK, lw=1)

    for key in ("connect", "join", "receive", "send", "users", "leave"):
        line((2.75, 10.9), key)
    for key in ("readonly", "receive", "users", "leave"):
        line((2.75, 3.9), key)
    for key in ("start", "monitor", "log", "config"):
        line((25.15, 10.1), key, "right")

    # «include»: both ways of joining include connecting to the server
    cx, cy, _ = uc["connect"]
    for key, rad in (("join", 0.45), ("readonly", 0.55)):
        x, y, _ = uc[key]
        ax.annotate("", xy=(cx + 2.3, cy - 0.05), xytext=(x + 2.3, y),
                    arrowprops=dict(arrowstyle="->", ls="--", color="#4338CA", lw=1,
                                    connectionstyle=f"arc3,rad={rad}"))
    ax.text(13.35, 12.6, "«include»", fontsize=7.8, color="#4338CA")
    ax.text(6.0, 1.05, "READ-ONLY users (joined without a username) receive messages, list users and leave, but cannot send.",
            fontsize=8, color=MUTED)
    save(fig, "use-case-diagram")


if __name__ == "__main__":
    deployment()
    sequence()
    use_cases()
    print("deployment, sequence and use case diagrams written to", OUT)
