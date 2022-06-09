package unimi.dsp.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Objects;

@XmlRootElement
public class TaxiInfoDto {
    private int id;
    private String ipAddress;
    private int port;

    public TaxiInfoDto() {}

    public TaxiInfoDto(int id, String ipAddress, int port) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getIpAddress() {
        return ipAddress;
    }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaxiInfoDto taxiInfoDto = (TaxiInfoDto) o;
        return Objects.equals(this.id, taxiInfoDto.id) &&
                Objects.equals(this.ipAddress, taxiInfoDto.ipAddress) &&
                Objects.equals(this.port, taxiInfoDto.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ipAddress, port);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TaxiInfo {\n");
        sb.append("    id: ").append(id).append("\n");
        sb.append("    ipAddress: ").append(ipAddress).append("\n");
        sb.append("    port: ").append(port).append("\n");
        sb.append("}");

        return sb.toString();
    }
}

