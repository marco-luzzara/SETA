package unimi.dsp;

import org.eclipse.paho.client.mqttv3.MqttException;
import unimi.dsp.SETA.SetaSystem;
import unimi.dsp.adminServer.ServerStarter;
import unimi.dsp.taxi.Taxi;

import java.io.IOException;

public class RunSimulation {
    public static void main(String[] args) throws InterruptedException {
        Thread server = new Thread(() -> {
            try {
                ServerStarter.main(new String[0]);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        server.start();
        Thread seta = new Thread(() -> {
            try {
                SetaSystem.main(new String[0]);
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        });
        seta.start();

        Thread.sleep(5000);

        for (int i = 0; i < Integer.parseInt(args[0]); i++) {
            final int localI = i;
            Thread taxi = new Thread(() ->
                    Taxi.main(new String[]{
                            Integer.toString(localI), "localhost", Integer.toString(5050 + localI)}));
            taxi.start();
        }
    }
}
