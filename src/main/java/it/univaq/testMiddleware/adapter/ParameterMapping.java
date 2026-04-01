package it.univaq.testMiddleware.adapter;

public class ParameterMapping {
    private String nome;
    private String tipologia;
    private String unitaMisura;
    private Double valMin;
    private Double valMax;

    public ParameterMapping(String nome, String tipologia, String unitaMisura, Double valMin, Double valMax) {
        this.nome = nome;
        this.tipologia = tipologia;
        this.unitaMisura = unitaMisura;
        this.valMin = valMin;
        this.valMax = valMax;
    }

    // Getters & Setters
    public String getNome() {
        return nome;
    }
    public String getTipologia() {
        return tipologia;
    }
    public String getUnitaMisura() {
        return unitaMisura;
    }
    public Double getValMin() {
        return valMin;
    }
    public Double getValMax() {
        return valMax;
    }
}
