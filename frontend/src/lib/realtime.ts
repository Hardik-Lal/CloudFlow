import { Client, type StompSubscription } from "@stomp/stompjs";
import { refreshSession } from "@/lib/api/client";
import { env } from "@/lib/env";
import { useAuthStore } from "@/stores/auth-store";

interface Registration {
  destination: string;
  onMessage: (body: string) => void;
  subscription: StompSubscription | null;
}

/**
 * One shared STOMP connection per tab. The access token goes in the CONNECT frame (browsers
 * cannot set headers on the WebSocket handshake) and is refreshed before every (re)connect.
 * Active subscriptions are re-attached after each reconnect.
 */
const registrations = new Set<Registration>();
let client: Client | null = null;

function attach(stomp: Client, registration: Registration) {
  registration.subscription = stomp.subscribe(registration.destination, (message) =>
    registration.onMessage(message.body),
  );
}

function getClient(): Client {
  if (client) {
    return client;
  }
  const stomp = new Client({
    brokerURL: env.apiBaseUrl.replace(/^http/, "ws") + "/ws",
    reconnectDelay: 3000,
    heartbeatIncoming: 20000,
    heartbeatOutgoing: 20000,
    beforeConnect: async (self) => {
      const token = useAuthStore.getState().accessToken ?? (await refreshSession()).accessToken;
      self.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => registrations.forEach((registration) => attach(stomp, registration)),
    onStompError: () => {
      // Usually an expired token: drop it so the reconnect refreshes the session first.
      useAuthStore.setState({ accessToken: null });
    },
  });
  stomp.activate();
  client = stomp;
  return stomp;
}

/** Subscribes to a topic until the returned function is called. */
export function subscribe<T>(destination: string, onMessage: (payload: T) => void): () => void {
  const stomp = getClient();
  const registration: Registration = {
    destination,
    onMessage: (body) => onMessage(JSON.parse(body) as T),
    subscription: null,
  };
  registrations.add(registration);
  if (stomp.connected) {
    attach(stomp, registration);
  }
  return () => {
    registrations.delete(registration);
    if (registration.subscription && stomp.connected) {
      registration.subscription.unsubscribe();
    }
  };
}

/** Closes the connection (on sign-out). */
export function disconnectRealtime() {
  registrations.clear();
  void client?.deactivate();
  client = null;
}
