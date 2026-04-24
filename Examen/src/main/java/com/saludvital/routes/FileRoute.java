package com.saludvital.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class FileRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("file:data/input?delete=true&include=.*\\.csv&exclude=\\._.*")
                .routeId("file-processing-route")

                .log("Procesando archivo: ${file:name}")

                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String[] lines = body.split("\n");

                    // Validar header
                    if (!lines[0].trim().equals("patient_id,full_name,appointment_date,insurance_code")) {
                        exchange.setProperty("isValid", false);
                        exchange.setProperty("error", "Header inválido");
                        return;
                    }

                    boolean valid = true;

                    for (int i = 1; i < lines.length; i++) {

                        String[] fields = lines[i].split(",");

                        if (fields.length != 4) {
                            valid = false;
                            exchange.setProperty("error", "Fila mal formada");
                            break;
                        }

                        // Campos vacíos
                        for (String f : fields) {
                            if (f.trim().isEmpty()) {
                                valid = false;
                                exchange.setProperty("error", "Campos vacíos");
                                break;
                            }
                        }

                        // Fecha
                        if (!fields[2].matches("\\d{4}-\\d{2}-\\d{2}")) {
                            valid = false;
                            exchange.setProperty("error", "Fecha inválida");
                            break;
                        }

                        // Seguro
                        if (!(fields[3].equals("IESS") ||
                                fields[3].equals("PRIVADO") ||
                                fields[3].equals("NINGUNO"))) {
                            valid = false;
                            exchange.setProperty("error", "Seguro inválido");
                            break;
                        }
                    }

                    exchange.setProperty("isValid", valid);
                })

                .choice()
                .when(exchangeProperty("isValid").isEqualTo(true))
                .log("Archivo válido → enviado a output")
                .to("file:data/output")
                .otherwise()
                .log("Archivo inválido: ${exchangeProperty.error}")
                .to("file:data/error")
                .end()

                // ARCHIVE con timestamp
                .process(exchange -> {
                    String originalName = exchange.getIn().getHeader("CamelFileName", String.class);

                    String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());

                    String newName = originalName.replace(".csv", "") + "_" + timestamp + ".csv";

                    exchange.getIn().setHeader("CamelFileName", newName);
                })

                .to("file:data/archive")

                .log("Archivo archivado correctamente");
    }
}