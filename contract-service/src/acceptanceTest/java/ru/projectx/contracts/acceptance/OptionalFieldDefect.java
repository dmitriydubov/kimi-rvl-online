package ru.projectx.contracts.acceptance;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class OptionalFieldDefect {

    private static final Pattern PARTICIPANT = Pattern.compile("([a-z_.]+) участника ([01])");
    private static final Map<String, String> PERSONAL_PATHS = Map.of(
            "program_id", "program_id",
            "middle_name", "personal_data.middle_name",
            "relation_type", "personal_data.relation_type",
            "address", "personal_data.address",
            "document.series", "personal_data.document.series",
            "document.issued_by", "personal_data.document.issued_by",
            "document.division_code", "personal_data.document.division_code",
            "document.issued_at", "personal_data.document.issued_at");

    private OptionalFieldDefect() {
    }

    static void apply(ObjectNode body, String defect) {
        String[] parts = defect.split(" — ", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("неизвестный дефект необязательного поля: " + defect);
        }
        apply(body, parts[0], parts[1]);
    }

    private static void apply(ObjectNode body, String target, String state) {
        if (target.equals("cancel_comment") || target.equals("policy_usk_number")) {
            write((ObjectNode) body.get("data"), target, state);
            return;
        }
        Matcher matcher = PARTICIPANT.matcher(target);
        if (!matcher.matches() || !PERSONAL_PATHS.containsKey(matcher.group(1))) {
            throw new IllegalArgumentException("неизвестное поле дефекта: " + target);
        }
        ObjectNode participant = participant(body, Integer.parseInt(matcher.group(2)));
        write(participant, PERSONAL_PATHS.get(matcher.group(1)), state);
    }

    private static ObjectNode participant(ObjectNode body, int index) {
        ArrayNode participants = (ArrayNode) body.get("data").get("participants");
        return (ObjectNode) participants.get(index);
    }

    private static void write(ObjectNode parent, String path, String state) {
        String[] names = path.split("\\.");
        ObjectNode container = parent;
        for (int index = 0; index < names.length - 1; index++) {
            container = (ObjectNode) container.get(names[index]);
        }
        writeLeaf(container, names[names.length - 1], state);
    }

    private static void writeLeaf(ObjectNode container, String name, String state) {
        switch (state) {
            case "ключ отсутствует" -> container.remove(name);
            case "JSON null" -> container.putNull(name);
            case "число 73001" -> container.put(name, 73001);
            case "boolean true" -> container.put(name, true);
            case "пустая строка" -> container.put(name, "");
            case "три пробела" -> container.put(name, "   ");
            case "строка \"не читается\"" -> container.put(name, "не читается");
            case "массив с ПДн" -> container.putArray(name).add("Тестов Тест Тестович");
            case "объект с ПДн" -> container.putObject(name).put("full_name", "Тестов Тест Тестович");
            default -> throw new IllegalArgumentException("неизвестный узел дефекта: " + state);
        }
    }
}
