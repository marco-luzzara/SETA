package unimi.dsp.testUtils.mocks;

import org.mockito.stubbing.OngoingStubbing;
import unimi.dsp.model.types.SmartCityPosition;

public class PositionGeneratorMock {
    OngoingStubbing<SmartCityPosition> ongoingStubbing;
    public PositionGeneratorMock(OngoingStubbing ongoingStubbing) {
        this.ongoingStubbing = ongoingStubbing;
    }

    public PositionGeneratorMock generate(int startX, int startY) {
        this.ongoingStubbing = this.ongoingStubbing.thenReturn(new SmartCityPosition(startX, startY));
        return this;
    }
}