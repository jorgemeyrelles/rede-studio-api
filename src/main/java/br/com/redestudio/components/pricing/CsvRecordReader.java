package br.com.redestudio.components.pricing;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de CSV (RFC 4180) em streaming: campo entre aspas, aspas dobradas
 * e quebra de linha dentro de aspas. Lê um registro por vez — o CSV de EC2
 * da AWS passa de 200 MB por Região e nunca fica inteiro na memória.
 */
public class CsvRecordReader {

    private final Reader reader;
    private List<String> header;
    private int pending = -2;

    public CsvRecordReader(Reader reader) {
        this.reader = reader;
    }

    /** Pula {@code lines} linhas físicas (o CSV da Price List tem 5 de metadados antes do cabeçalho). */
    public void skipLines(int lines) throws IOException {
        for (int i = 0; i < lines; i++) {
            int c;
            while ((c = read()) != -1 && c != '\n') {
                // descarta até o fim da linha
            }
            if (c == -1) {
                return;
            }
        }
    }

    /** Próximo registro como mapa coluna → valor (a primeira chamada lê o cabeçalho); {@code null} no fim. */
    public Map<String, String> nextRow() throws IOException {
        if (header == null) {
            header = nextRecord();
            if (header == null) {
                return null;
            }
        }
        List<String> values = nextRecord();
        if (values == null) {
            return null;
        }
        Map<String, String> row = new LinkedHashMap<>(header.size() * 2);
        for (int i = 0; i < header.size(); i++) {
            row.put(header.get(i), i < values.size() ? values.get(i) : "");
        }
        return row;
    }

    /** Próximo registro como lista de campos; {@code null} no fim do arquivo. */
    public List<String> nextRecord() throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        int c;
        while ((c = read()) != -1) {
            any = true;
            if (quoted) {
                if (c == '"') {
                    int next = read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        unread(next);
                    }
                } else {
                    field.append((char) c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                fields.add(field.toString());
                return fields;
            } else if (c != '\r') {
                field.append((char) c);
            }
        }
        if (!any) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }

    private int read() throws IOException {
        if (pending != -2) {
            int c = pending;
            pending = -2;
            return c;
        }
        return reader.read();
    }

    private void unread(int c) {
        pending = c;
    }
}
