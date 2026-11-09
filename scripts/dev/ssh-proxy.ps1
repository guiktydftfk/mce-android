param([string]$TargetHost, [string]$TargetPort)
$ErrorActionPreference = "Stop"
$src = @"
using System;
using System.Net.Sockets;

public class ProxyBridge {
    public static int Run(string host, string port) {
        var client = new TcpClient();
        client.Connect("127.0.0.1", 7897);
        var stream = client.GetStream();
        var req = System.Text.Encoding.ASCII.GetBytes(
            "CONNECT " + host + ":" + port + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\n\r\n");
        stream.Write(req, 0, req.Length);
        stream.Flush();
        var sb = new System.Text.StringBuilder();
        int b;
        while ((b = stream.ReadByte()) >= 0) {
            sb.Append((char)b);
            if (sb.ToString().EndsWith("\r\n\r\n")) break;
        }
        if (!sb.ToString().Contains("200")) {
            Console.Error.WriteLine("proxy CONNECT failed: " + sb.ToString());
            return 1;
        }
        var stdin = Console.OpenStandardInput();
        var stdout = Console.OpenStandardOutput();
        var pump = new System.Threading.Thread(() => {
            try {
                var buf = new byte[16384];
                int n;
                while ((n = stdin.Read(buf, 0, buf.Length)) > 0) {
                    stream.Write(buf, 0, n);
                    stream.Flush();
                }
                try { client.Client.Shutdown(SocketShutdown.Send); } catch { }
            } catch { }
        });
        pump.IsBackground = true;
        pump.Start();
        try {
            var buf2 = new byte[16384];
            int m;
            while ((m = stream.Read(buf2, 0, buf2.Length)) > 0) {
                stdout.Write(buf2, 0, m);
                stdout.Flush();
            }
        } catch { }
        try { client.Close(); } catch { }
        return 0;
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp
exit [ProxyBridge]::Run($TargetHost, $TargetPort)
