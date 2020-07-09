package io.jenkins.plugins.luxair.model;

public class ImageTag {
    private final String image;
    private final String tag;

    public ImageTag(String image, String tag) {
        this.image = image;
        this.tag = tag;
    }

    public String getImage() {
        return image;
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

        return image.equals(that.image)
            && tag.equals(that.tag);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result +
            ((image == null) ? 0 : image.hashCode()) +
            ((tag == null) ? 0 : tag.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return image + ":" + tag;
    }
}
