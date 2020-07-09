package io.jenkins.plugins.luxair.model;

public class ImageTag {
    private final String name;
    private final String tag;

    public ImageTag(String name, String tag) {
        this.name = name;
        this.tag = tag;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!super.equals(obj)) return false;

        ImageTag that = (ImageTag) obj;

        return name.equals(that.name)
            && tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result +
            ((name == null) ? 0 : name.hashCode()) +
            ((tag == null) ? 0 : tag.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return name + ":" + tag;
    }
}
