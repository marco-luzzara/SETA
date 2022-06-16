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

    @Override
    public void addTaxi(TaxiServiceOuterClass.TaxiAddRequest request, StreamObserver<Empty> responseObserver) {
        this.taxi.addNewTaxi(request);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void removeTaxi(TaxiServiceOuterClass.TaxiRemoveRequest request, StreamObserver<Empty> responseObserver) {
        this.taxi.removeTaxi(request);
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
