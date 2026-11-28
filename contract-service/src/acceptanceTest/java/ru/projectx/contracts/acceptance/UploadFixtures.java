package ru.projectx.contracts.acceptance;

import java.util.Map;
import java.util.Set;

final class UploadFixtures {

    private static final Set<String> PARTICIPANTS = Set.of(
            "2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6",
            "4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6",
            "5d8b3f71-2e9a-4c06-b4f8-a1c7e0d29b13",
            "6778e8c2-985a-4d54-9bd3-0f6403b104d6",
            "9275471c-d2fe-484f-97a6-d6d7b1267b5e",
            "ae8312c3-350b-4d0b-806c-5e2460e74dbc",
            "e4a91b78-0e23-4c69-9f55-1b8c2a36f014");
    private static final Set<String> POLICIES = Set.of(
            "0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64",
            "13ab5388-61ee-4e21-8a46-c325478b78c0");
    private static final Set<String> FAMILY_MEMBERS = Set.of(
            "2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6",
            "4d8e1f2a-3b4c-4d5e-9f60-718293a4b5c6");
    private static final Set<String> ATTACHMENTS = Set.of(
            "0c6a9f31-58b2-4e77-91d0-3a7e5b1c22f9",
            "1a4c7e2f-9b30-4d85-a6f1-c2e8b05d7f39",
            "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21",
            "7e91d4b2-0c38-4f5a-8b17-2d6e9a3f0c54");
    private static final Set<String> FILENAMES = Set.of(
            "Тест_документ.jpg",
            "Тест_согласие_1.jpg",
            "Тест_согласие_11.jpg",
            "Тест_согласие_2.jpg",
            "Тестов_Пётр_070у.jpg",
            "Тестов_Пётр_070у.png",
            "Тестов_Пётр_согласие.png",
            "Тестов_согласие.jpg",
            "Тестов_согласие.png",
            "Тестов_третий_070у.jpg",
            "Тестова_Анна_070у.jpg",
            "Тестова_Анна_070у.png",
            "Тестова_Анна_070у_стр2.jpg",
            "Тестова_Анна_070у_стр2.pdf",
            "Тестова_Анна_070у_стр3.jpg",
            "Тестова_Анна_070у_стр4.jpg",
            "Тестова_Анна_Ивановна_J45.gif",
            "Тестова_Анна_Ивановна_J45.jpg",
            "Тестова_Анна_Ивановна_J45.png",
            "Тестова_Анна_согласие.jpg",
            "Тестова_Анна_согласие.pdf",
            "Тестова_Мария_070у.jpg");
    private static final Set<String> CONTENTS = Set.of(
            "СТЕНД: вымышленная справка 070/у в PDF",
            "СТЕНД: вымышленная справка 070/у, исправленная",
            "СТЕНД: вымышленная справка 070/у, страница 2",
            "СТЕНД: вымышленная справка 070/у, участник 1",
            "СТЕНД: вымышленная справка 070/у, участник 2",
            "СТЕНД: вымышленное согласие на обработку ПДн в PDF",
            "СТЕНД: вымышленное согласие на обработку ПДн, исправленное",
            "СТЕНД: вымышленное согласие на обработку ПДн, участник 1",
            "СТЕНД: вымышленное согласие на обработку ПДн, участник 2",
            "СТЕНД: вымышленный диагноз J45.0 по МКБ-10");
    private static final Set<String> DELETE_REQUESTS = Set.of(
            "delete-request-001", "delete-request-002", "delete-request-003");

    private UploadFixtures() {
    }

    static String participant(String value) {
        return known(PARTICIPANTS, "участник", value);
    }

    static String policy(String value) {
        return known(POLICIES, "policy_id", value);
    }

    static String familyMember(String value) {
        return known(FAMILY_MEMBERS, "family_member_id", value);
    }

    static String attachment(Map<String, String> fields) {
        return known(ATTACHMENTS, "вложение", StepTables.required(fields, "вложение"));
    }

    static String filename(Map<String, String> fields) {
        return known(FILENAMES, "имя файла", StepTables.required(fields, "имя файла"));
    }

    static String content(String value) {
        return known(CONTENTS, "содержимое", value);
    }

    static String deleteRequest(String value) {
        return known(DELETE_REQUESTS, "запрос удаления", value);
    }

    private static String known(Set<String> values, String kind, String value) {
        if (!values.contains(value)) {
            throw new IllegalArgumentException("неизвестное стендовое значение «" + kind + "»: " + value);
        }
        return value;
    }
}
