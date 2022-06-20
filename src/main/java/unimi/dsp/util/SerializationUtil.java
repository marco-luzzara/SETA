package unimi.dsp.util;

import com.google.gson.Gson;
import unimi.dsp.dto.RideRequestDto;

import java.nio.charset.StandardCharsets;

public class SerializationUtil {
    public static byte[] serialize(Object obj) {
        String jsonRideRequest = new Gson().toJson(obj);
        return jsonRideRequest.getBytes(StandardCharsets.UTF_8);
    }

    public static <T> T deserialize(byte[] objBytes, Class<T> objCls) {
        String content = new String(objBytes, StandardCharsets.UTF_8);
        return new Gson().fromJson(content, objCls);
    }
}
