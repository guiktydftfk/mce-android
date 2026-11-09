$ErrorActionPreference = "Stop"
$src = @"
using System;
using System.Net;
using System.Net.Sockets;
using System.Threading;

public class SshTunnel {
    public static void Run(int listenPort, string targetHost, int targetPort) {
        var listener = new TcpListener(IPAddress.Loopback, listenPort);
        listener.Start();
        Console.WriteLine("listening " + listenPort + " -> " + targetHost + ":" + targetPort);
        while (true) {
            var inbound = listener.AcceptTcpClient();
            var th = new Thread(() => Handle(inbound, targetHost, targetPort));
            th.IsBackground = true;
            th.Start();
        }
    }

    static void Handle(TcpClient inbound, string host, int port) {
        TcpClient upstream = null;
        try {
            upstream = new TcpClient();
            upstream.Connect("127.0.0.1", 7897);
            var up = upstream.GetStream();
            var req = System.Text.Encoding.ASCII.GetBytes(
                "CONNECT " + host + ":" + port + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\n\r\n");
            up.Write(req, 0, req.Length);
            up.Flush();
            var sb = new System.Text.StringBuilder();
            int b;
            while ((b = up.ReadByte()) >= 0) {
                sb.Append((char)b);
                if (sb.ToString().EndsWith("\r\n\r\n")) break;
            }
            if (!sb.ToString().Contains("200")) {
                inbound.Close();
                upstream.Close();
                return;
            }
            var down = inbound.GetStream();
            var t1 = new Thread(() => Pump(down, up));
            t1.IsBackground = true;
            t1.Start();
            Pump(up, down);
        } catch {
            try { inbound.Close(); } catch { }
            try { if (upstream != null) upstream.Close(); } catch { }
        }
    }

    static void Pump(NetworkStream from, NetworkStream to) {
        try {
            var buf = new byte[32768];
            int n;
            while ((n = from.Read(buf, 0, buf.Length)) > 0) {
                to.Write(buf, 0, n);
                to.Flush();
            }
        } catch { }
        try { to.Close(); } catch { }
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp
[SshTunnel]::Run(2222, "ssh.github.com", 443)
