package es.codeurjc.squirrel.drey.local;

public class AlgorithmEvent {

    String algorithmId;
    String name;
    Object content;

    public AlgorithmEvent(String algorithmId, String name, Object content) {
        this.algorithmId = algorithmId;
        this.name = name;
        this.content = content;
    }

    public String getAlgorithmId() {
        return algorithmId;
    }

    public void setAlgorithmId(String algorithmId) {
        this.algorithmId = algorithmId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

}
