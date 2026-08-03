package com.aiplayer.examples;

import com.aiplayer.engine.AIPlayer;
import com.aiplayer.protocol.L2JProtocol;

/**
 * B3 live-connect probe: attempts a real login against the running LoginServer (:2106)
 * using the B2 handshake. Prints how far it gets so framing can be iterated.
 * Usage: LoginProbe <account> <password> <charId>
 */
public class LoginProbe {
    public static void main(String[] args) {
        String account = args.length > 0 ? args[0] : "ai_combat_01";
        String password = args.length > 1 ? args[1] : "ai123pass";
        int charId = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        try {
            AIPlayer player = new AIPlayer(account, 100, 1, 0);
            L2JProtocol protocol = new L2JProtocol(player, "127.0.0.1", 2106, 7777);
            System.out.println("[LoginProbe] attempting login " + account + " -> 127.0.0.1:2106 ...");
            long t0 = System.currentTimeMillis();
            boolean ok = protocol.connectAndLogin(account, password, charId);
            long t1 = System.currentTimeMillis();
            System.out.println("[LoginProbe] RESULT connectAndLogin=" + ok
                    + " in " + (t1 - t0) + "ms; connected=" + protocol.isConnected()
                    + " loggedIn=" + protocol.isLoggedIn());
            protocol.disconnect();
            System.exit(ok ? 0 : 2);
        } catch (Throwable t) {
            System.out.println("[LoginProbe] EXCEPTION: " + t);
            t.printStackTrace(System.out);
            System.exit(3);
        }
    }
}
