package plugin;

import org.bukkit.Material;

public class Treasure {

  private String materialName;
  private int bonusScore;
  private String jpName;

  public Material getTreasureMaterial() {
    return Material.getMaterial(materialName);
  }

  // lombokなしでgetter/setterを自分で書く例
  public String getMaterialName() {
    return materialName;
  }
  public void setMaterialName(String materialName) {
    this.materialName = materialName;
  }
  public int getBonusScore() {
    return bonusScore;
  }
  public void setBonusScore(int bonusScore) {
    this.bonusScore = bonusScore;
  }
  public String getJpName() {
    return jpName;
  }
  public void setJpName(String jpName) {
    this.jpName = jpName;
  }
}