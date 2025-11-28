package dev.dimitra.bot;

public class SmellyExample {

    // Long parameter list + does too many things + long method + duplication
    public int doEverything(
            int a, int b, int c, int d, int e, int f,
            String op,
            boolean log,
            boolean useCache,
            boolean sendMetrics
    ) {
        int result = 0;

        // --- Operation handling (long method, low cohesion) ---
        if ("add".equals(op)) {
            result = a + b + c + d + e + f;
            if (log) {
                System.out.println("Adding numbers: " + a + "," + b + "," + c + "," + d + "," + e + "," + f);
            }
            if (sendMetrics) {
                sendMetric("add", result);
            }
        } else if ("multiply".equals(op)) {
            result = a * b * c * d * e * f;
            if (log) {
                System.out.println("Multiplying numbers: " + a + "," + b + "," + c + "," + d + "," + e + "," + f);
            }
            if (sendMetrics) {
                sendMetric("multiply", result);
            }
        } else if ("average".equals(op)) {
            result = (a + b + c + d + e + f) / 6;
            if (log) {
                System.out.println("Averaging numbers: " + a + "," + b + "," + c + "," + d + "," + e + "," + f);
            }
            if (sendMetrics) {
                sendMetric("average", result);
            }
        }

        // Fake “cache” logic (unrelated responsibility in same method)
        if (useCache) {
            System.out.println("Checking cache for result of operation " + op + "...");
            // pretend we hit a cache
        }

        // Some useless loop to make the method even longer
        for (int i = 0; i < 10; i++) {
            result += i;
        }

        return result;
    }

    private void sendMetric(String op, int value) {
        System.out.println("METRIC: operation=" + op + ", value=" + value);
    }

    // Another long parameter list, different responsibility (notifications)
    public void handleUserNotifications(
            String userName,
            String email,
            String address,
            String phone,
            String taxId,
            String notes,
            boolean sendEmail,
            boolean sendSms
    ) {
        if (sendEmail) {
            System.out.println("Sending EMAIL to " + email + " for user " + userName);
        }
        if (sendSms) {
            System.out.println("Sending SMS to " + phone + " for user " + userName);
        }
        // Totally different responsibility than doEverything → low cohesion / big class
    }
}
