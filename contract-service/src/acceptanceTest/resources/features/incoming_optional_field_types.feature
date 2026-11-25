# language: ru
#
# Источники правил (в комментариях к сценариям — по коротким меткам):
#   [ТИ-01]  «Точки интеграций сервис Договоров и Заявок — редакция 2», ДГ-01: необязательные program_id, middle_name,
#            relation_type, address и реквизиты документа имеют типы guid, string и date; нарушение контракта, в том числе
#            значение не того типа, отклоняет сообщение целиком без ответа; отсутствие program_id у участника с выпуском и
#            relation_type у родственника — ошибка по участнику PROGRAM_REQUIRED / RELATION_TYPE_REQUIRED.
#   [ТИ-02]  там же, ДГ-02: cancel_comment — необязательный строковый комментарий; обработанное прекращение отвечает ДГ-03.
#   [ТИ-07]  там же, ДГ-07: policy_usk_number — необязательный строковый номер; status ISSUED / ERROR; при ERROR код и текст
#            1С:УСК сохраняются и передаются в ДГ-03 как пришли.
#   [Д-06]   «Журнал решений Договоров», Д-06: ДГ-03 уходит на ошибку по участнику, прекращение и завершение работы.
#   [Д-09]   там же, Д-09: нарушение контракта отклоняется сразу; события Ordering записываются в журнал входящих и
#            пропускаются по своим правилам, а не по правилам этой фичи.
#   [CS-Н]   contract-service/CLAUDE.md, «Надёжность и исключения»: невалидное сообщение — постоянная ошибка без повторов;
#            бизнес-ошибка по участнику — сообщение обработано, участник ERROR с кодом и текстом в ДГ-03.
#   [CS-ПДн] там же, «Персональные данные»: в ошибке называется поле, но не значение; ПДн не пишутся в журнал и тексты
#            исключений.
#   [OPS]    docs/operations.md, «Возврат отклонённого входящего сообщения в разбор»: возврат FAILED в NEW повторяет разбор
#            того же тела с новым счётом попыток и не уменьшает счётчик отказов.
#   [К15]    задание К15: отсутствие ключа и JSON null дают пустое значение; любой нестроковый узел в необязательных
#            текстовых, GUID- и датовых полях ДГ-01, ДГ-02 и ДГ-07 ISSUED — нарушение контракта с причиной
#            «недопустимое значение поля <полный путь>»; разбор происходит до бизнес-транзакции; ДГ-07 ERROR это поле не
#            читает; пустая строка и строка пробелов не меняются; события Ordering вне задачи.
#
# Словарь — как в requests_task_inbox.feature, requests_task_issuance.feature, requests_task_cancellation.feature,
# lm_policy_issued.feature и inbox_manual_return.feature. Новые шаги публикации принимают «… с дефектом "D" …».
# D — закрытая категория, а не JSON и не свободный текст: шаг признаёт только дословные значения из «Примеров» этой фичи
# и отклоняет любое другое внутри step-определения. Часть до « — » задаёт путь: «program_id участника N» —
# data.participants[N].program_id; «middle_name участника N» — data.participants[N].personal_data.middle_name;
# «relation_type участника N» — …personal_data.relation_type; «address участника N» — …personal_data.address;
# «document.series участника N» — …personal_data.document.series; то же для document.issued_by, document.division_code
# и document.issued_at; «cancel_comment» — data.cancel_comment; «policy_usk_number» — data.policy_usk_number.
# Часть после « — » задаёт узел: «ключ отсутствует» — ключ удалён; «JSON null» — null; «число 73001» — 73001;
# «boolean true» — true; «пустая строка» — ""; «три пробела» — "   "; «строка "не читается"» — "не читается";
# «массив с ПДн» — ["Тестов Тест Тестович"]; «объект с ПДн» — {"full_name":"Тестов Тест Тестович"}.
# «JSON-значение поля "P" сообщения "M" есть в его теле, но не попало …» — шаг сам читает фактический узел из
# опубликованного тела по пути P и проверяет, что его нет ни в причине исхода, ни в журнале; ожидаемый узел параметром
# не передаётся. «значение поля … — пусто / пустая строка / три пробела» — различает null, "" и "   " без обрезания.
# Остальные исходы, записи выпуска, ответы, журнал и счётчики проверяются существующими формулировками.
# GUID, даты и персональные данные вымышлены.
#
# Вне фичи:
#  - конверт входящих сообщений и синхронные вызовы /internal/v1;
#  - PaymentOrderEvent от Ordering: по Д-09 события с пустыми или нестроковыми полями записываются и пропускаются без FAILED;
#  - уже работающие проверки необязательного целого documents[i].file_size и массива documents, необязательный текст ДГ-05;
#  - доменные проверки пустых строк и строк из пробелов: здесь закрепляется только их передача как есть в текстовых полях;
#  - правки существующих фич, README и документации; пояснение операции исправления отклонённого прекращения — после
#    утверждения этой спецификации отдельной документационной правкой.

Функция: Необязательные текстовые, GUID- и датовые поля входящих сообщений принимают только строку, отсутствие ключа или JSON null

  Отсутствие необязательного поля и JSON null сохраняют прежнее поведение. Число, boolean, массив или объект больше не
  превращаются молча в пустое значение: всё сообщение отклоняется как нарушившее контракт до изменения записей выпуска.
  Причина называет полный путь поля, но не раскрывает значение узла. Для ДГ-07 со статусом ERROR необязательный номер не
  читается и не мешает применить ошибку 1С:УСК. Пустые строки не обрезаются и не отклоняются этим правилом, а неканонические
  GUID и дата отклоняются по-прежнему.

  Предыстория:
    Дано часы Договоров остановлены на "2026-02-10 10:05:00" по Москве
    И нумерация договоров КМ начата заново
    И у сервиса Заявок есть заявка:
      | заявка              | 61a7982c-a0b4-4bbf-9527-60b40b752803 |
      | номер               | IZ-2026-004512                       |
      | массовая заявка     | c9bfd8fc-7ed2-4fcc-a67f-b8afe814b36f |
      | договор страхования | e12aab1a-1582-4b3a-af36-d30e637953f1 |
      | полис страхователя  | a23ba061-f742-4b20-8b95-36a0e525c087 |
      | ЛПУ                 | 1b1b4fdf-bd2a-4055-a127-8c24c544a23f |
      | заезд               | 2026-04-10                           |
      | выезд               | 2026-04-24                           |
    И у справочного сервиса есть программа:
      | id          | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
      | external_id | 6b02f1cc-9d47-4e35-8a20-7f1c3e5b09a2 |
      | в архиве    | false                                |
    И у справочного сервиса есть договор страхования:
      | id          | e12aab1a-1582-4b3a-af36-d30e637953f1 |
      | external_id | 8c41e2ab-55d0-4a17-9b63-2f0e7c1d4a88 |
      | в архиве    | false                                |
    И у справочного сервиса есть полис:
      | id          | a23ba061-f742-4b20-8b95-36a0e525c087 |
      | external_id | 1d9a6e77-4c02-4b88-a15e-30f7b2c9de41 |
      | номер       | СТЕНД-0001                           |
      | в архиве    | false                                |
    И у справочного сервиса есть полис:
      | id          | 13ab5388-61ee-4e21-8a46-c325478b78c0 |
      | external_id | 5f0b2a77-8c31-4f2e-9d44-7c1a0b93e512 |
      | номер       | 0555123456                           |
      | в архиве    | false                                |

  # [ТИ-01], [Д-09], [CS-Н], [CS-ПДн], [К15]: все 8 полей × 4 нестроковых вида × участник NONE / уже ISSUED.
  # Второе сообщение также содержит нового валидного участника с доплатой: у него записи тоже не появляются.
  Структура сценария: Нестроковый узел любого необязательного поля задачи отклоняет всё сообщение до применения любого участника
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    И Договоры готовят запросы на выпуск полисов ЛМ
    И Договоры публикуют исходящие сообщения
    И 1С:УСК публикует в свою очередь результат выпуска "5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05" по полису ЛМ участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | status             | ISSUED                               |
      | policy_usk_id      | cb904f2a-7d61-4e08-89a5-40b1e3c7f012 |
      | policy_usk_number  | LM-000012204                         |
      | policy.external_id | 5f0b2a77-8c31-4f2e-9d44-7c1a0b93e512 |
      | policy.number      | 0555123456                           |
      | policy.date_from   | 2026-04-10                           |
      | policy.date_to     | 2026-04-24                           |
    И Договоры разобрали присланные сообщения
    И Договоры публикуют исходящие сообщения
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    Когда сервис Заявок публикует в свою очередь задачу на выпуск "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 2 с дефектом "<дефект>" и участниками:
      | участник                             | родственник | доплата  | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00     |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00     | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
      | 2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6 | да          | 34000.00 | PARENT  | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля <поле>"
    И JSON-значение поля "<поле>" сообщения "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f" есть в его теле, но не попало ни в причину исхода, ни в журнал Договоров
    И в журнале Договоров есть запись уровня ERROR об окончательном отказе сообщения "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f": тип события "ContractTaskUpserted", попытка 1, SQLSTATE "нет"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskUpserted" вырос за сценарий на 1
    И выпуск по участнику "ae8312c3-350b-4d0b-806c-5e2460e74dbc" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | ничего |
    И записи о выпуске участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" в порядке создания — "полис ЛМ ISSUED"
    И выпуск по участнику "2b9d4e6f-8a1c-4d3e-9f50-61728394a5b6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | ничего |
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    Когда на часах Договоров прошло 60 минут
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля <поле>"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskUpserted" вырос за сценарий на 1
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1

    Примеры:
      | дефект                                       | поле                                                   |
      | program_id участника 0 — число 73001         | data.participants[0].program_id                        |
      | program_id участника 0 — boolean true        | data.participants[0].program_id                        |
      | program_id участника 0 — массив с ПДн        | data.participants[0].program_id                        |
      | program_id участника 0 — объект с ПДн        | data.participants[0].program_id                        |
      | program_id участника 1 — число 73001         | data.participants[1].program_id                        |
      | program_id участника 1 — boolean true        | data.participants[1].program_id                        |
      | program_id участника 1 — массив с ПДн        | data.participants[1].program_id                        |
      | program_id участника 1 — объект с ПДн        | data.participants[1].program_id                        |
      | middle_name участника 0 — число 73001        | data.participants[0].personal_data.middle_name         |
      | middle_name участника 0 — boolean true       | data.participants[0].personal_data.middle_name         |
      | middle_name участника 0 — массив с ПДн       | data.participants[0].personal_data.middle_name         |
      | middle_name участника 0 — объект с ПДн       | data.participants[0].personal_data.middle_name         |
      | middle_name участника 1 — число 73001        | data.participants[1].personal_data.middle_name         |
      | middle_name участника 1 — boolean true       | data.participants[1].personal_data.middle_name         |
      | middle_name участника 1 — массив с ПДн       | data.participants[1].personal_data.middle_name         |
      | middle_name участника 1 — объект с ПДн       | data.participants[1].personal_data.middle_name         |
      | relation_type участника 0 — число 73001      | data.participants[0].personal_data.relation_type       |
      | relation_type участника 0 — boolean true     | data.participants[0].personal_data.relation_type       |
      | relation_type участника 0 — массив с ПДн     | data.participants[0].personal_data.relation_type       |
      | relation_type участника 0 — объект с ПДн     | data.participants[0].personal_data.relation_type       |
      | relation_type участника 1 — число 73001      | data.participants[1].personal_data.relation_type       |
      | relation_type участника 1 — boolean true     | data.participants[1].personal_data.relation_type       |
      | relation_type участника 1 — массив с ПДн     | data.participants[1].personal_data.relation_type       |
      | relation_type участника 1 — объект с ПДн     | data.participants[1].personal_data.relation_type       |
      | address участника 0 — число 73001            | data.participants[0].personal_data.address             |
      | address участника 0 — boolean true           | data.participants[0].personal_data.address             |
      | address участника 0 — массив с ПДн           | data.participants[0].personal_data.address             |
      | address участника 0 — объект с ПДн           | data.participants[0].personal_data.address             |
      | address участника 1 — число 73001            | data.participants[1].personal_data.address             |
      | address участника 1 — boolean true           | data.participants[1].personal_data.address             |
      | address участника 1 — массив с ПДн           | data.participants[1].personal_data.address             |
      | address участника 1 — объект с ПДн           | data.participants[1].personal_data.address             |
      | document.series участника 0 — число 73001    | data.participants[0].personal_data.document.series     |
      | document.series участника 0 — boolean true   | data.participants[0].personal_data.document.series     |
      | document.series участника 0 — массив с ПДн   | data.participants[0].personal_data.document.series     |
      | document.series участника 0 — объект с ПДн   | data.participants[0].personal_data.document.series     |
      | document.series участника 1 — число 73001    | data.participants[1].personal_data.document.series     |
      | document.series участника 1 — boolean true   | data.participants[1].personal_data.document.series     |
      | document.series участника 1 — массив с ПДн   | data.participants[1].personal_data.document.series     |
      | document.series участника 1 — объект с ПДн   | data.participants[1].personal_data.document.series     |
      | document.issued_by участника 0 — число 73001  | data.participants[0].personal_data.document.issued_by  |
      | document.issued_by участника 0 — boolean true | data.participants[0].personal_data.document.issued_by  |
      | document.issued_by участника 0 — массив с ПДн | data.participants[0].personal_data.document.issued_by  |
      | document.issued_by участника 0 — объект с ПДн | data.participants[0].personal_data.document.issued_by  |
      | document.issued_by участника 1 — число 73001  | data.participants[1].personal_data.document.issued_by  |
      | document.issued_by участника 1 — boolean true | data.participants[1].personal_data.document.issued_by  |
      | document.issued_by участника 1 — массив с ПДн | data.participants[1].personal_data.document.issued_by  |
      | document.issued_by участника 1 — объект с ПДн | data.participants[1].personal_data.document.issued_by  |
      | document.division_code участника 0 — число 73001  | data.participants[0].personal_data.document.division_code |
      | document.division_code участника 0 — boolean true | data.participants[0].personal_data.document.division_code |
      | document.division_code участника 0 — массив с ПДн | data.participants[0].personal_data.document.division_code |
      | document.division_code участника 0 — объект с ПДн | data.participants[0].personal_data.document.division_code |
      | document.division_code участника 1 — число 73001  | data.participants[1].personal_data.document.division_code |
      | document.division_code участника 1 — boolean true | data.participants[1].personal_data.document.division_code |
      | document.division_code участника 1 — массив с ПДн | data.participants[1].personal_data.document.division_code |
      | document.division_code участника 1 — объект с ПДн | data.participants[1].personal_data.document.division_code |
      | document.issued_at участника 0 — число 73001  | data.participants[0].personal_data.document.issued_at  |
      | document.issued_at участника 0 — boolean true | data.participants[0].personal_data.document.issued_at  |
      | document.issued_at участника 0 — массив с ПДн | data.participants[0].personal_data.document.issued_at  |
      | document.issued_at участника 0 — объект с ПДн | data.participants[0].personal_data.document.issued_at  |
      | document.issued_at участника 1 — число 73001  | data.participants[1].personal_data.document.issued_at  |
      | document.issued_at участника 1 — boolean true | data.participants[1].personal_data.document.issued_at  |
      | document.issued_at участника 1 — массив с ПДн | data.participants[1].personal_data.document.issued_at  |
      | document.issued_at участника 1 — объект с ПДн | data.participants[1].personal_data.document.issued_at  |

  # [ТИ-01], [К15]: отсутствие и null — пустое значение; "" и "   " сохраняются дословно, доменные проверки вне фичи.
  Структура сценария: Отсутствие и null необязательных полей, а также пустые строки в текстовых полях сохраняют прежнее поведение
    Когда сервис Заявок публикует в свою очередь задачу на выпуск "1e5a7c93-4b2d-4f68-9a0c-7d3e5f1b2a4c" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с дефектом "<дефект>" и участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "1e5a7c93-4b2d-4f68-9a0c-7d3e5f1b2a4c" обработано
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ |
      | статус   | NEW      |
    И значение поля "<поле снимка>" выпуска участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" — "<форма>"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskUpserted" вырос за сценарий на 0
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 0
    И в журнале Договоров нет записей уровня ERROR

    Примеры:
      | дефект                                             | поле снимка                            | форма         |
      | middle_name участника 1 — ключ отсутствует         | personal_data.middle_name              | пусто         |
      | middle_name участника 1 — JSON null                | personal_data.middle_name              | пусто         |
      | middle_name участника 1 — пустая строка            | personal_data.middle_name              | пустая строка |
      | middle_name участника 1 — три пробела              | personal_data.middle_name              | три пробела   |
      | relation_type участника 1 — пустая строка          | personal_data.relation_type            | пустая строка |
      | relation_type участника 1 — три пробела            | personal_data.relation_type            | три пробела   |
      | address участника 1 — ключ отсутствует             | personal_data.address                  | пусто         |
      | address участника 1 — JSON null                    | personal_data.address                  | пусто         |
      | address участника 1 — пустая строка                | personal_data.address                  | пустая строка |
      | address участника 1 — три пробела                  | personal_data.address                  | три пробела   |
      | document.series участника 1 — ключ отсутствует     | personal_data.document.series          | пусто         |
      | document.series участника 1 — JSON null            | personal_data.document.series          | пусто         |
      | document.series участника 1 — пустая строка        | personal_data.document.series          | пустая строка |
      | document.series участника 1 — три пробела          | personal_data.document.series          | три пробела   |
      | document.issued_by участника 1 — ключ отсутствует  | personal_data.document.issued_by       | пусто         |
      | document.issued_by участника 1 — JSON null         | personal_data.document.issued_by       | пусто         |
      | document.issued_by участника 1 — пустая строка     | personal_data.document.issued_by       | пустая строка |
      | document.issued_by участника 1 — три пробела       | personal_data.document.issued_by       | три пробела   |
      | document.division_code участника 1 — ключ отсутствует | personal_data.document.division_code | пусто         |
      | document.division_code участника 1 — JSON null     | personal_data.document.division_code   | пусто         |
      | document.division_code участника 1 — пустая строка | personal_data.document.division_code   | пустая строка |
      | document.division_code участника 1 — три пробела   | personal_data.document.division_code   | три пробела   |
      | document.issued_at участника 1 — ключ отсутствует  | personal_data.document.issued_at       | пусто         |
      | document.issued_at участника 1 — JSON null         | personal_data.document.issued_at       | пусто         |

  # [ТИ-01], [CS-Н], [Д-06], [К15]: условно обязательные поля отсутствуют или null — прежняя бизнес-ошибка, не отказ сообщения.
  Структура сценария: Отсутствие program_id или relation_type и null сохраняют прежнюю бизнес-ошибку участника
    Когда сервис Заявок публикует в свою очередь задачу на выпуск "2a4c6e80-1b3d-4f5a-9c7e-0d2f4a6b8c1e" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с дефектом "<дефект>" и участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "2a4c6e80-1b3d-4f5a-9c7e-0d2f4a6b8c1e" обработано
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ     | полис ЛМ       |
      | статус       | ERROR          |
      | код ошибки   | <код ошибки>   |
      | текст ошибки | <текст ошибки> |
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskUpserted" вырос за сценарий на 0
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    И сообщение о состоянии выпуска №1: заявка "61a7982c-a0b4-4bbf-9527-60b40b752803", статус "ERROR", участники:
      | участник                             | document_kind | km_contract_state | error_code     | error_message   |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | NONE          | NOT_REQUIRED      |                |                 |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | LM_POLICY     | NOT_REQUIRED      | <код ошибки>   | <текст ошибки>  |

    Примеры:
      | дефект                                    | код ошибки             | текст ошибки                                                                            |
      | program_id участника 1 — ключ отсутствует | PROGRAM_REQUIRED       | поле participants[1].program_id обязательно для участника, которому выпускается документ |
      | program_id участника 1 — JSON null        | PROGRAM_REQUIRED       | поле participants[1].program_id обязательно для участника, которому выпускается документ |
      | relation_type участника 1 — ключ отсутствует | RELATION_TYPE_REQUIRED | поле participants[1].personal_data.relation_type обязательно для родственника           |
      | relation_type участника 1 — JSON null     | RELATION_TYPE_REQUIRED | поле participants[1].personal_data.relation_type обязательно для родственника           |

  # [ТИ-01], [Д-09], [К15]: пустая строка и пробелы — неканонические GUID / дата, а не отсутствие значения.
  Структура сценария: Пустая строка и строка пробелов в необязательных GUID- и датовых полях отклоняются как неканонические
    Когда сервис Заявок публикует в свою очередь задачу на выпуск "3b5d7f91-2c4e-4a6b-8d9f-1e3a5b7c9d2f" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с дефектом "<дефект>" и участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "3b5d7f91-2c4e-4a6b-8d9f-1e3a5b7c9d2f" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля <поле>"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskUpserted" вырос за сценарий на 1
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | ничего |
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 0

    Примеры:
      | дефект                                       | поле                                                  |
      | program_id участника 1 — пустая строка       | data.participants[1].program_id                       |
      | program_id участника 1 — три пробела         | data.participants[1].program_id                       |
      | document.issued_at участника 1 — пустая строка | data.participants[1].personal_data.document.issued_at |
      | document.issued_at участника 1 — три пробела | data.participants[1].personal_data.document.issued_at |

  # [ТИ-02], [Д-09], [CS-ПДн], [К15]: нестроковый комментарий не применяется, активные записи сохраняют прежние статусы.
  Структура сценария: Нестроковый cancel_comment отклоняет прекращение с первой попытки и не меняет активные договор и полис
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "4c6e8a02-3d5f-4b7c-9e0a-2f4b6d8c0e1a" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата  | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00     |         |                                      |
      | 9275471c-d2fe-484f-97a6-d6d7b1267b5e | да          | 34000.00 | SPOUSE  | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00     | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    Когда сервис Заявок публикует в свою очередь прекращение работы "5d7f9b13-4e6a-4c8d-0f1b-3a5c7e9d0f2b" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 2 с дефектом "<дефект>":
      | причина      | INSURED_REFUSED           |
      | аннулировано | 2026-02-12T14:30:00+03:00 |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "5d7f9b13-4e6a-4c8d-0f1b-3a5c7e9d0f2b" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля data.cancel_comment"
    И JSON-значение поля "data.cancel_comment" сообщения "5d7f9b13-4e6a-4c8d-0f1b-3a5c7e9d0f2b" есть в его теле, но не попало ни в причину исхода, ни в журнал Договоров
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskCancelled" вырос за сценарий на 1
    И выпуск по участнику "9275471c-d2fe-484f-97a6-d6d7b1267b5e" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | договор КМ  |
      | статус   | DRAFT_READY |
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ |
      | статус   | NEW      |
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    Когда на часах Договоров прошло 60 минут
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "5d7f9b13-4e6a-4c8d-0f1b-3a5c7e9d0f2b" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля data.cancel_comment"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskCancelled" вырос за сценарий на 1
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1

    Примеры:
      | дефект                        |
      | cancel_comment — число 73001  |
      | cancel_comment — boolean true |
      | cancel_comment — массив с ПДн |
      | cancel_comment — объект с ПДн |

  # [ТИ-02], [Д-06], [К15]: отсутствие и null — комментария нет; "" и "   " переносятся дословно.
  Структура сценария: Отсутствие, null и пустые строки cancel_comment сохраняют прежнее прекращение
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "6e8a0c24-5f7b-4d9e-1a2c-4b6d8f0a1c3e" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата  | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00     |         |                                      |
      | 9275471c-d2fe-484f-97a6-d6d7b1267b5e | да          | 34000.00 | SPOUSE  | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00     | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    Когда сервис Заявок публикует в свою очередь прекращение работы "7f9a1d35-6a8c-4e0f-2b3d-5c7e9a1f2d4b" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 2 с дефектом "<дефект>":
      | причина      | INSURED_REFUSED           |
      | аннулировано | 2026-02-12T14:30:00+03:00 |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "7f9a1d35-6a8c-4e0f-2b3d-5c7e9a1f2d4b" обработано
    И выпуск по участнику "9275471c-d2fe-484f-97a6-d6d7b1267b5e" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | договор КМ |
      | статус   | CANCELLED  |
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ  |
      | статус   | CANCELLED |
    И значение поля "cancel_comment" выпуска участника "9275471c-d2fe-484f-97a6-d6d7b1267b5e" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" — "<форма>"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskCancelled" вырос за сценарий на 0
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 2
    И сообщение о состоянии выпуска №2: заявка "61a7982c-a0b4-4bbf-9527-60b40b752803", статус "CANCELLED", участники:
      | участник                             | document_kind | km_contract_state |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | NONE          | NOT_REQUIRED      |
      | 9275471c-d2fe-484f-97a6-d6d7b1267b5e | KM_CONTRACT   | CANCELLED         |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | LM_POLICY     | NOT_REQUIRED      |

    Примеры:
      | дефект                            | форма         |
      | cancel_comment — ключ отсутствует | пусто         |
      | cancel_comment — JSON null        | пусто         |
      | cancel_comment — пустая строка    | пустая строка |
      | cancel_comment — три пробела      | три пробела   |

  # [OPS], [К15]: ручной возврат не исправляет тело; исправление приходит новым сообщением с новым message_id.
  Структура сценария: Возврат отклонённого прекращения повторяет отказ, а исправленное сообщение с новым message_id прекращает работу
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "8a0b2e46-7c9d-4f1a-3c4e-6d8f0b2a4c6e" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата  | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00     |         |                                      |
      | 9275471c-d2fe-484f-97a6-d6d7b1267b5e | да          | 34000.00 | SPOUSE  | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00     | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    И сервис Заявок публикует в свою очередь прекращение работы "9b1c3f57-8d0e-4a2b-4d5f-7e9a0c3b5d7f" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 2 с дефектом "cancel_comment — число 73001":
      | причина      | INSURED_REFUSED           |
      | аннулировано | 2026-02-12T14:30:00+03:00 |
    И Договоры разбирают присланные сообщения
    И сообщение сервиса Заявок "9b1c3f57-8d0e-4a2b-4d5f-7e9a0c3b5d7f" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля data.cancel_comment"
    Когда дежурный возвращает в разбор сообщение сервиса Заявок "9b1c3f57-8d0e-4a2b-4d5f-7e9a0c3b5d7f" по процедуре ручного возврата
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "9b1c3f57-8d0e-4a2b-4d5f-7e9a0c3b5d7f" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля data.cancel_comment"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskCancelled" вырос за сценарий на <рост отказов>
    И выпуск по участнику "9275471c-d2fe-484f-97a6-d6d7b1267b5e" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | договор КМ  |
      | статус   | DRAFT_READY |
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ |
      | статус   | NEW      |
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    Когда сервис Заявок публикует в свою очередь прекращение работы "ac2d4e68-9f1a-4b3c-8e6a-0f2b4d6c8e1a" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 3:
      | причина      | INSURED_REFUSED                  |
      | комментарий  | Стенд: исправленный комментарий |
      | аннулировано | 2026-02-12T14:30:00+03:00        |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение сервиса Заявок "ac2d4e68-9f1a-4b3c-8e6a-0f2b4d6c8e1a" обработано
    И выпуск по участнику "9275471c-d2fe-484f-97a6-d6d7b1267b5e" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ    | договор КМ                       |
      | статус      | <статус после исправления>       |
      | комментарий | Стенд: исправленный комментарий |
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ  |
      | статус   | CANCELLED |
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "ContractTaskCancelled" вырос за сценарий на <рост отказов>
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 2

    Примеры:
      | статус после исправления | рост отказов |
      | CANCELLED                | 2            |

  # [ТИ-07], [Д-09], [CS-ПДн], [К15]: нестроковый номер при ISSUED отклоняется, полис не меняется.
  Структура сценария: Нестроковый policy_usk_number при ISSUED отклоняет ответ 1С:УСК и оставляет полис переданным
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "bd3e5f79-0a2b-4c4d-9f7b-1a3c5e7d9f0b" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    И Договоры готовят запросы на выпуск полисов ЛМ
    И Договоры публикуют исходящие сообщения
    Когда 1С:УСК публикует в свою очередь результат выпуска "ce4f6a80-1b3c-4d5e-0a8c-2b4d6f8e0a1c" по полису ЛМ участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" с дефектом "<дефект>":
      | status             | ISSUED                               |
      | policy_usk_id      | cb904f2a-7d61-4e08-89a5-40b1e3c7f012 |
      | policy.external_id | 5f0b2a77-8c31-4f2e-9d44-7c1a0b93e512 |
      | policy.number      | 0555123456                           |
      | policy.date_from   | 2026-04-10                           |
      | policy.date_to     | 2026-04-24                           |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение 1С:УСК "ce4f6a80-1b3c-4d5e-0a8c-2b4d6f8e0a1c" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля data.policy_usk_number"
    И JSON-значение поля "data.policy_usk_number" сообщения "ce4f6a80-1b3c-4d5e-0a8c-2b4d6f8e0a1c" есть в его теле, но не попало ни в причину исхода, ни в журнал Договоров
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "LmPolicyIssued" вырос за сценарий на 1
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ  |
      | статус   | SUBMITTED |
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 0
    Когда на часах Договоров прошло 60 минут
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение 1С:УСК "ce4f6a80-1b3c-4d5e-0a8c-2b4d6f8e0a1c" отклонено окончательно после попытки 1 с причиной "недопустимое значение поля data.policy_usk_number"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "LmPolicyIssued" вырос за сценарий на 1
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 0

    Примеры:
      | дефект                            |
      | policy_usk_number — число 73001   |
      | policy_usk_number — boolean true  |
      | policy_usk_number — массив с ПДн  |
      | policy_usk_number — объект с ПДн  |

  # [ТИ-07], [Д-06], [К15]: отсутствие и null — номера нет; "" и "   " сохраняются дословно.
  Структура сценария: Отсутствие, null и пустые строки policy_usk_number не мешают штатному выпуску
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "df5a7b91-2c4d-4e6f-1b9d-3c5e7a0f1b2d" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    И Договоры готовят запросы на выпуск полисов ЛМ
    И Договоры публикуют исходящие сообщения
    Когда 1С:УСК публикует в свою очередь результат выпуска "e06b8c02-3d5e-4f7a-2c0e-4d6f8b1a2c3e" по полису ЛМ участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" с дефектом "<дефект>":
      | status             | ISSUED                               |
      | policy_usk_id      | cb904f2a-7d61-4e08-89a5-40b1e3c7f012 |
      | policy.external_id | 5f0b2a77-8c31-4f2e-9d44-7c1a0b93e512 |
      | policy.number      | 0555123456                           |
      | policy.date_from   | 2026-04-10                           |
      | policy.date_to     | 2026-04-24                           |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение 1С:УСК "e06b8c02-3d5e-4f7a-2c0e-4d6f8b1a2c3e" обработано
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ | полис ЛМ |
      | статус   | ISSUED   |
    И значение поля "policy_usk_number" выпуска участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" — "<форма>"
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "LmPolicyIssued" вырос за сценарий на 0
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    И сообщение о состоянии выпуска №1: заявка "61a7982c-a0b4-4bbf-9527-60b40b752803", статус "COMPLETED", участники:
      | участник                             | document_kind | km_contract_state | policy_number |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | NONE          | NOT_REQUIRED      |               |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | LM_POLICY     | NOT_REQUIRED      | 0555123456    |
    И в журнале Договоров нет записей уровня ERROR

    Примеры:
      | дефект                               | форма         |
      | policy_usk_number — ключ отсутствует | пусто         |
      | policy_usk_number — JSON null        | пусто         |
      | policy_usk_number — пустая строка    | пустая строка |
      | policy_usk_number — три пробела      | три пробела   |

  # [ТИ-07], [CS-Н], [Д-06], [К15]: при ERROR необязательный номер не читается независимо от узла.
  Структура сценария: При ERROR любой узел policy_usk_number не мешает применить ошибку 1С:УСК
    Дано сервис Заявок публикует в свою очередь задачу на выпуск "f17c9d13-4e6f-4a8b-3d1f-5e7a0c2b4d6f" по заявке "61a7982c-a0b4-4bbf-9527-60b40b752803" версии 1 с участниками:
      | участник                             | родственник | доплата | родство | программа                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | нет         | 0.00    |         |                                      |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | да          | 0.00    | CHILD   | 2f6a0c1e-3d5b-4a7c-9e8f-0b1c2d3e4f5a |
    И Договоры разобрали присланные сообщения
    И Договоры готовят запросы на выпуск полисов ЛМ
    И Договоры публикуют исходящие сообщения
    Когда 1С:УСК публикует в свою очередь результат выпуска "028d0e24-5f7a-4b9c-4e2a-6f8b1d3c5e7a" по полису ЛМ участника "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803" с дефектом "<дефект>":
      | status        | ERROR                                                 |
      | error_code    | INSURED_NOT_COVERED                                   |
      | error_message | Стенд: застрахованный не входит в корпоративный договор |
    И Договоры разбирают присланные сообщения
    И Договоры публикуют исходящие сообщения
    Тогда сообщение 1С:УСК "028d0e24-5f7a-4b9c-4e2a-6f8b1d3c5e7a" обработано
    И выпуск по участнику "6778e8c2-985a-4d54-9bd3-0f6403b104d6" заявки "61a7982c-a0b4-4bbf-9527-60b40b752803":
      | документ         | полис ЛМ                                                |
      | статус           | ERROR                                                   |
      | код ошибки       | INSURED_NOT_COVERED                                     |
      | текст ошибки     | Стенд: застрахованный не входит в корпоративный договор |
      | номер в 1С       |                                                         |
      | выпущенный полис |                                                         |
    И счётчик Prometheus "contracts_inbox_failed_total" с меткой event_type "LmPolicyIssued" вырос за сценарий на 0
    И сервис Заявок получает из очереди "состояние выпуска" сообщения о состоянии выпуска, всего за сценарий — 1
    И сообщение о состоянии выпуска №1: заявка "61a7982c-a0b4-4bbf-9527-60b40b752803", статус "ERROR", участники:
      | участник                             | document_kind | km_contract_state | error_code          | error_message                                            |
      | ae8312c3-350b-4d0b-806c-5e2460e74dbc | NONE          | NOT_REQUIRED      |                     |                                                          |
      | 6778e8c2-985a-4d54-9bd3-0f6403b104d6 | LM_POLICY     | NOT_REQUIRED      | INSURED_NOT_COVERED | Стенд: застрахованный не входит в корпоративный договор |
    И в журнале Договоров нет записей уровня ERROR

    Примеры:
      | дефект                                 |
      | policy_usk_number — ключ отсутствует   |
      | policy_usk_number — JSON null          |
      | policy_usk_number — строка "не читается" |
      | policy_usk_number — число 73001        |
      | policy_usk_number — boolean true       |
      | policy_usk_number — массив с ПДн       |
      | policy_usk_number — объект с ПДн       |
