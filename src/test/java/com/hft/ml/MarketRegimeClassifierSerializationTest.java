package com.hft.ml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

public class MarketRegimeClassifierSerializationTest {

    @Test
    void trainedModel_isSerializable_roundtrip() throws Exception {
        MarketRegimeClassifier clf = new MarketRegimeClassifier();
        // Create TrainedModel wrapper (may be untrained) and serialize
        MarketRegimeClassifier.TrainedModel model = clf.getModel();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(model);
        }

        byte[] bytes = bos.toByteArray();
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object obj = ois.readObject();
            assertThat(obj).isInstanceOf(MarketRegimeClassifier.TrainedModel.class);
            MarketRegimeClassifier.TrainedModel round = (MarketRegimeClassifier.TrainedModel) obj;
            assertThat(round.getVersion()).isEqualTo(model.getVersion());
        }
    }
}
