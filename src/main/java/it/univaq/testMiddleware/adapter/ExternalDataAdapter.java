package it.univaq.testMiddleware.adapter;

import it.univaq.testMiddleware.models.*;
import it.univaq.testMiddleware.DTO.*;

public interface ExternalDataAdapter {
    ExternalDataResponse fetchAndMapData(Condominio condominio, Dispositivo dispositivo, User user);
}
