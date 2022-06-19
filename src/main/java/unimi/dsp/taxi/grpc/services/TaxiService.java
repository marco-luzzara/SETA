package unimi.dsp.taxi.grpc.services;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import unimi.dsp.taxi.Taxi;
import unimi.dsp.taxi.TaxiServiceGrpc;
import unimi.dsp.taxi.TaxiServiceOuterClass;

public class TaxiService extends TaxiServiceGrpc.TaxiServiceImplBase {
    private final Taxi taxi;

    public TaxiService(Taxi taxi) {
        this.taxi = taxi;
    }

    // the remote taxi presents to this taxi, and receives the coordinate of this taxi
    @Override
    public void addTaxi(TaxiServiceOuterClass.TaxiAddRequest request,
                        StreamObserver<TaxiServiceOuterClass.TaxiAddResponse> responseObserver) {
        this.taxi.addRemoteTaxi(request);
        responseObserver.onNext(
                TaxiServiceOuterClass.TaxiAddResponse.newBuilder()
                        .setX(taxi.getX())
                        .setY(taxi.getY())
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeTaxi(TaxiServiceOuterClass.TaxiRemoveRequest request, StreamObserver<Empty> responseObserver) {
        this.taxi.removeRemoteTaxi(request.getId());
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
