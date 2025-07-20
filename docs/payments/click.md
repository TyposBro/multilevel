

    Описание
    SHOP-API
        Общее
        Запросы
        Ошибки
        Тестирование
    Кнопка оплаты
    Merchant-API
    Оплата через Telegram
    Плагины для CMS
    Пример реализации
    Мобильная интеграция


Описание

    Главная
    Описание

Интерфейсы оплаты подразделяются на две категории
Интерфейсы оплаты

    Мобильное приложение CLICK SuperApp
    Web сервис оплаты my.click.uz
    USSD сервис
    Telegram bot

Интерфейсы оплаты поставщика

    Сайт поставщика
    Мобильное приложение поставщика

При удовлетворении требований к биллинг системе поставщика (реализация запросов Prepare и Complete описанных в разделе SHOP API) услуги поставщика могут быть доступны для оплаты через любой из интерфейсов оплаты.
© 2025 Click

Click-API — Общее

    Главная
    Click-API — Общее

Общие положения

Данный документ описывает порядок взаимодействия биллинг системы Поставщика товаров и услуг (в т.ч. Интернет-магазины) с системой CLICK через интерфейс взаимодействия «CLICK-API» (далее API) для реализации он-лайн продаж своих товаров и услуг через Web-сайт, Е-mail/SMS – рассылку или другие имеющиеся у Поставщика инструменты.
Термины и определения

Система CLICK – система, позволяющая производить оплату посредством мобильного телефона (через USSD\SMS-портал) или интернета (через WEB\WEB-mobile) за услуги сотовых операторов, интернет-провайдеров; переводить денежные средства другим физическим лицам, торгово-сервисным предприятиям; делать онлайн-покупки в интернет-магазинах и др. со счетов, открытых в банке.

Поставщик — юридическое лицо, реализующее товары или услуги пользователям.

Пользователь – физическое лицо, подключенное к системе CLICK.

Сервисы поставщика – объекты реализации поставщика. Сервисами могут являться услуги и товары.

Биллинг система Поставщика – система Поставщика для учета пользователей, товаров или услуг Поставщика. Аппаратно-программное обеспечение, позволяющее вести учетно-расчетные операции в электронном виде между поставщиком и пользователем с сохранением истории операций, так же предоставляющее API-интерфейс для взаимодействия с внешними системами оплат.

© 2025 Click


Click-API – Запросы

    Главная
    Click-API – Запросы

Описание взаимодействия

Взаимодействие осуществляется через API-интерфейс, расположенный на сервере Поставщика. API-интерфейс полностью должен соответствовать требованиям данного документа. Созданный платеж в системе CLICK передается по протоколу HTTP (HTTPS) методом POST API- интерфейсу Поставщика услуг. Поставщик предоставляет системе CLICK URL-адреса для взаимодействия с API-интерфейсом. Взаимодействие разделено на два этапа:

1. Prepare

2. Complete

Описание этапов проведения оплаты:
Подготовка и проверка платежа (Prepare). Action = 0.
Параметры запроса:
# 	Наименование параметра 	Тип данных 	Описание
1 	click_trans_id 	bigint 	Номер транзакции (итерации) в системе CLICK, т.е. попытки провести платеж.
2 	service_id 	int 	ID сервиса
3 	click_paydoc_id 	bigint 	Номер платежа в системе CLICK. Отображается в СМС у клиента при оплате.
4 	merchant_trans_id 	varchar 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика
5 	amount 	float 	Сумма оплаты (в сумах)
6 	action 	int 	Выполняемое действие. Для Prepare — 0
7 	error 	int 	Код статуса завершения платежа. 0 – успешно. В случае ошибки возвращается код ошибки.
8 	error_note 	varchar 	Описание кода завершения платежа.
9 	sign_time 	varchar 	Дата платежа. Формат «YYYY-MM-DD HH:mm:ss»
10 	sign_string 	varchar 	Проверочная строка, подтверждающая подлинность отправляемого запроса. ХЭШ MD5 из следующих параметров:

md5( click_trans_id + service_id + SECRET_KEY* + merchant_trans_id + amount + action + sign_time)

SECRET_KEY – уникальная строка, выдаваемая Поставщику при подключении.
Параметры ответа:
# 	Наименование параметра 	Тип данных 	Описание
1 	click_trans_id 	bigint 	ID платежа в системе CLICK
2 	merchant_trans_id 	varchar 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика
3 	merchant_prepare_id 	int 	ID платежа в биллинг системе поставщика для подтверждения
4 	error 	int 	Код статуса запроса. 0 – успешно. В случае ошибки возвращается код ошибки
5 	error_note 	varchar 	Описание кода завершения платежа.

Данным запросом система CLICK по параметрам платежа (merchant_trans_id, amount) проверяет:

a. Наличие сформированного заказа/логина/номера лиц.счета в биллинг системе Поставщика, его актуальность, а также возможность Поставщиком отпустить указанный в заказе товар или услугу.

Примечание. Для интернет-магазина, при получении запроса поставщик по указанному номеру заказа должен зарезервировать соответствующий товар, для предотвращения «перехвата» его другим и покупки одного и того же товара несколькими покупателями.

b. Актуальность суммы заказа или оплаты.

На данный запрос Поставщик должен вернуть состояние заказа/логина/номера лиц.счета:
a. Заказ/логин/номера лиц.счета и сумма актуальны. Ожидает оплаты.
При получении данного статуса со стороны системы CLICK будет отправлен запрос Complete.

b. Заказ/логин/номер лиц.счета неактуален (отменен). При данном ответе со стороны CLICK будет отправлен запрос Complete c признаком отмены платежа.
Примечание. При получении отрицательного ответа от Поставщика в случае если уже средства со счета Пользователя были списаны (запрос давался повторно, если на предыдущий запрос Complete система CLICK не дождалась ответа и не знает о состоянии заказа.), то система CLICK, будет давать запрос Complete с признаком подтверждения платежа. Если средства не списаны, то CLICK также аннулирует платеж.

c. Заказ ранее был подтвержден. При данном ответе со стороны системы CLICK будет завершен платеж. Запрос Complete повторно направлен не будет.

Примечание. Поставщик должен в своей биллинг системе поставить защиту от повторного проведения платежа, ранее уже подтвержденного, с одним и тем же click_trans_id.

Завершение платежа (Complete). Action = 1.
Параметры запроса:
# 	Наименование параметра 	Тип данных 	Описание
1 	click_trans_id 	bigint 	ID платежа в системе CLICK
2 	service_id 	int 	ID сервиса
3 	click_paydoc_id 	bigint 	Номер платежа в системе CLICK. Отображается в СМС у клиента при оплате.
4 	merchant_trans_id 	varchar 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика
5 	merchant_prepare_id 	int 	ID платежа в биллинг системе поставщика для подтверждения, полученный при запросе «Prepare»
6 	amount 	float 	Сумма оплаты (в сумах)
7 	action 	int 	Выполняемое действие. Для Complete — 1
8 	error 	int 	Код статуса завершения платежа. 0 – успешно. В случае ошибки возвращается код ошибки.
9 	error_note 	varchar 	Описание кода завершения платежа.
10 	sign_time 	varchar 	Дата платежа. Формат «YYYY-MM-DD HH:mm:ss»
11 	sign_string 	varchar 	Проверочная строка, подтверждающая подлинность отправляемого запроса. ХЭШ MD5 из следующих параметров:

md5( click_trans_id + service_id + SECRET_KEY* + merchant_trans_id + merchant_prepare_id + amount + action + sign_time )

SECRET_KEY – уникальная строка, выдаваемая Поставщику при подключении.
Параметры ответа:
# 	Наименование параметра 	Тип данных 	Описание
1 	click_trans_id 	bigint 	ID платежа в системе CLICK
2 	merchant_trans_id 	varchar 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика
3 	merchant_confirm_id 	int 	ID транзакции завершения платежа в биллинг системе (может быть NULL)
4 	error 	int 	Код статуса запроса. 0 – успешно. В случае ошибки возвращается код ошибки.
5 	error_note 	varchar 	Описание кода завершения платежа.

Данный запрос завершает процесс он-лайн оплаты. При получении от Поставщика услуг положительного ответа на запрос Prepare система CLICK проверяет возможность оплаты заказа Пользователем. В зависимости от успешности списания средств, запрос Complete содержит параметр error со следующими значениями:

1. «0» – Успешно. Посылается при успешном списании средств. При этом Поставщик должен отпустить товар или оказать оплаченную услугу.

2. «<=-1» – Отмена. Посылается при ошибке списания средств или при других ошибках, возвращается код ошибки. При этом Поставщик должен снять бронь (при имении таковой) с товаров, а так же вернуть ответ с кодом -9 (см. таблицу ответов). При отмене со стороны CLICK будет так же отправлено описание ошибки.

Примечание.
При успешном ответе на запрос Prepare и успешном списании средств с карты, ответ на запрос Complete не может быть ошибочным (кроме случаев, когда платеж уже ранее был подтвержден error = -4 или идет повторная попытка подтвердить ранее отмененного платежа error = -9). При получении ошибочного ответа от поставщика, после нескольких попыток, платеж зависнет для ручного разбирательства со стороны Службы технической поддержки CLICK.


При необходимости, если возникла ошибка предоставления услуг/продажи товара после успешного списания средств с карты и во время выполнения запроса Complete, то Биллинг поставщика отвечает на запрос Complete «успешно» и отправляет запрос на «отмену платежа» (см. документацию CLICK-API-MERCHANT пункт 3. Payment_cancel)
© 2025 Click


Click-API – Ошибки

    Главная
    Click-API – Ошибки

Примеры ответов

Поставщик услуг возвращает ответ в формате JSON следующие параметры:

При успешно проведенной операции
# 	Наименование параметра 	Тип данных 	Описание
1 	error 	int 	Код статуса операции. При успешно выполненной операции код должен быть равен «0»
2 	error_note 	varchar 	Описание статуса
3 	click_trans_id 	int 	ID платежа системы CLICK
4 	merchant_trans_id 	varchar 	ID платежа системы Онлайн Магазина
5 	merchant_prepare_id
или
merchant_confirm_id 	int 	ID платежа в биллинг системе поставщика для подтверждения
При ошибке проведения операции
# 	Наименование
параметра 	Тип данных 	Описание
1 	error 	int 	Код статуса операции.
2 	error_note 	varchar 	Описание статуса
Коды ошибок возвращаемые системой Сlick
# 	error 	error_note 	Описание
1 	0 	Success 	Код статуса операции. При успешно выполненной операции код должен быть равен «0»
2 	< 0 	Описание ошибки 	При получении отрицательного кода ошибки Поставщик должен аннулировать платеж в биллинговой системе и вернуть error код -9
Коды ошибок возвращаемые системой поставщика
# 	error 	error_note 	Описание
1 	0 	Success 	Успешный запрос
2 	-1 	SIGN CHECK FAILED! 	Ошибка проверки подписи
3 	-2 	Incorrect parameter amount 	Неверная сумма оплаты
4 	-3 	Action not found 	Запрашиваемое действие не найдено
5 	-4 	Already paid 	Транзакция ранее была подтверждена (при попытке подтвердить или отменить ранее подтвержденную транзакцию)
6 	-5 	User does not exist 	Не найдет пользователь/заказ (проверка параметра merchant_trans_id)
7 	-6 	Transaction does not exist 	Не найдена транзакция (проверка параметра merchant_prepare_id)
8 	-7 	Failed to update user 	Ошибка при изменении данных пользователя (изменение баланса счета и т.п.)
9 	-8 	Error in request from click 	Ошибка в запросе от CLICK (переданы не все параметры и т.п.)
10 	-9 	Transaction cancelled 	Транзакция ранее была отменена (При попытке подтвердить или отменить ранее отмененную транзакцию)
© 2025 Click


Click-API – Тестирование

    Главная
    Click-API – Тестирование

Тестирование и отладка

Для тестирования и отладки API-интерфейса в процессе разработки необходимо использовать данное программное обеспечение (далее ПО), которое эмулирует действия системы CLICK. Поставщик конфигурирует ПО и пошагово проходит тесты по загруженным сценариям.

Описание полей для заполнения
# 	Наименование параметра 	Тип данных 	Описание
1 	Prepare URL 	varchar 	Ссылка на обработчик API-интерфейса для запроса Prepare
2 	Complete URL 	varchar 	Ссылка на обработчик API-интерфейса для запроса Complete
3 	service_id 	int 	Идентификатор сервиса, полученный при регистрации в системе CLICK
4 	merchant_user_id 	int 	Идентификатор сервиса, полученный при регистрации в системе CLICK
5 	secret_key 	varchar 	Секретный ключ для участия в формировании подписи, полученный при регистрации в системе CLICK
6 	merchant_trans_id 	varchar 	ID платежа системы Онлайн Магазина
7 	prepare/confirm_id 	read only 	Заполняется при выполнении сценариев автоматически

 

После заполнения данных, необходимо выбрать сценарий из выпадающего списка и нажать кнопку «Начать тест». Описание каждого сценария можно увидеть в таблице «Описание сценариев». Определенные сценарии, после успешного выполнения могут перейти на следующий сценарий автоматически. Какие конкретно сценарии имеют автоматический переход можно так же увидеть в таблице «Описание сценариев», столбец «Перейти на сценарий». Выбранный сценарий из выпадающего списка сформирует параметры, соответствующие параметрам из таблицы «Описание сценариев». Каждый тест проходит с подробным описанием передаваемых параметров и получаемого ответа в окне «Лог тестирования». В данном окне можно увидеть запрос/ответ и проанализировать выявленные ошибки. В случае успешного выполнения сценария в окне «Лог тестирования» будет написано, что сценарий пройден, а так же в таблице «Описание сценариев» данный сценарий будет помечен «Выполнено» в поле «Статус теста».
Все сценарии необходимо успешно пройти. После прохождения всех тестов необходимо нажать кнопку «Сформировать отчет».

Замечание: Указанные URL в программе должны быть доступны для ПО для тестирования (может быть на локальном компьютере).

При этом запустится процедура сверки данных, после отправит все необходимые параметры на сервер регистрации CLICK. В окне «Лог тестирования» можно увидеть состояние выполнения данной процедуры. Для данной процедуры необходим доступ в сеть Интернет. Сообщение «Статус регистрации отчета: Регистрация успешно завершена!» будет означать успешное добавление данных в CLICK.

Успешное добавление данных можно проверить через http://merchant.click.uz
© 2025 Click


Click — Установка кнопки оплаты

    Главная
    Click — Установка кнопки оплаты

Вариант 1 — Переход по ссылке:

Для перехода на страницу оплаты CLICK необходимо создать кнопку (ссылку) на следующий адрес:
https://my.click.uz/services/pay?service_id={service_id}&merchant_id={merchant_id}&amount={amount}&transaction_param={transaction_param}&return_url={return_url}&card_type={card_type}
# 	Наименование параметра 	Тип данных 	Описание
1 	merchant_id 	mandatory 	ID поставщика
2 	merchant_user_id 	optional 	ID пользователя в системе поставщиков
3 	service_id 	mandatory 	ID Сервиса поставщика
4 	transaction_param 	mandatory 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика. Соответствует merchant_trans_id из SHOP-API
5 	amount 	mandatory 	Сумма транзакции (формат: N.NN)
6 	return_url 	optional 	Ссылка, на которую пользователь попадёт после оплаты
7 	card_type 	optional 	Тип платёжной системы (uzcard, humo)

* mandatory – обязательный параметр
* optional – необязательный параметр

Пример оформления кнопки (CSS) указан ниже в варианте 2
Вариант 2 — переход по HTML форме:
<form action="https://my.click.uz/services/pay" method="get" target="_blank">
                                    <button typo="submit" class="pay_with_click"><i></i>Оплатить через CLICK</button>
                                    <input type=”hidden” name=”KEY” value=”VALUE” />
                                    <input type=”hidden” name=”KEY” value=”VALUE” />
                                    …
</form>

Для взаимодействия сайта поставщика и интерфейса оплаты CLICK необходимо передать следующие параметры c помощью hidden полей.
# 	Наименование параметра 	Тип данных 	Описание
1 	merchant_id 	mandatory 	ID поставщика
2 	merchant_user_id 	optional 	ID пользователя в системе поставщиков
3 	service_id 	mandatory 	ID Сервиса поставщика
4 	transaction_param 	mandatory 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика. Соответствует merchant_trans_id из SHOP-API
5 	amount 	mandatory 	Сумма транзакции (формат: N.NN)
6 	return_url 	optional 	Ссылка, на которую пользователь попадёт после оплаты
7 	card_type 	optional 	Тип платёжной системы (uzcard, humo)

* mandatory – обязательный параметр
* optional – необязательный параметр
Пример формирования формы (PHP Код):
<?
$merchantID = 20; //Нужно заменить параметр на полученный ID
$merchantUserID = 4;
$serviceID = 31;  $transID = "user23151";
$transAmount = number_format(1000, 2, '.', '');
$returnURL = "сайт поставщика";
$HTML = <<<CODE
<form action="https://my.click.uz/services/pay" id=”click_form” method="get" target="_blank">
                                    <input type="hidden" name="amount" value="$transAmount" />
                                    <input type="hidden" name="merchant_id" value="$merchantID"/>
                                    <input type="hidden" name="merchant_user_id" value="$merchantUserID"/>
                                    <input type="hidden" name="service_id" value="$serviceID"/>
                                    <input type="hidden" name="transaction_param" value="$transID"/>
                                    <input type="hidden" name="return_url" value="$returnURL"/>
                                    <input type="hidden" name="card_type" value="$cardType"/>
                                    <button type="submit" class="click_logo"><i></i>Оплатить через CLICK</button>                         
</form>
CODE;
Пример конечного HTML кода:
<form id="click_form" action="https://my.click.uz/services/pay" method="get" target="_blank">
<input type="hidden" name="amount" value="1000" />
<input type="hidden" name="merchant_id" value="46"/>
<input type="hidden" name="merchant_user_id" value="4"/>
<input type="hidden" name="service_id" value="36"/>
<input type="hidden" name="transaction_param" value="user23151"/>
<input type="hidden" name="return_url" value="сайт поставщика"/>
<input type="hidden" name="card_type" value="uzcard/humo"/>
<button type="submit" class="click_logo"><i></i>Оплатить через CLICK</button>
</form>
 

CSS код для кнопки:
.click_logo {
padding:4px 10px;
cursor:pointer;
color: #fff;
line-height:190%;
font-size: 13px;
font-family: Arial;
font-weight: bold;
text-align: center;
border: 1px solid #037bc8;
text-shadow: 0px -1px 0px #037bc8;
border-radius: 4px;
background: #27a8e0;
background: url(data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiA/Pgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjEwMCUiIGhlaWdodD0iMTAwJSIgdmlld0JveD0iMCAwIDEgMSIgcHJlc2VydmVBc3BlY3RSYXRpbz0ibm9uZSI+CiAgPGxpbmVhckdyYWRpZW50IGlkPSJncmFkLXVjZ2ctZ2VuZXJhdGVkIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjAlIiB5MT0iMCUiIHgyPSIwJSIgeTI9IjEwMCUiPgogICAgPHN0b3Agb2Zmc2V0PSIwJSIgc3RvcC1jb2xvcj0iIzI3YThlMCIgc3RvcC1vcGFjaXR5PSIxIi8+CiAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0b3AtY29sb3I9IiMxYzhlZDciIHN0b3Atb3BhY2l0eT0iMSIvPgogIDwvbGluZWFyR3JhZGllbnQ+CiAgPHJlY3QgeD0iMCIgeT0iMCIgd2lkdGg9IjEiIGhlaWdodD0iMSIgZmlsbD0idXJsKCNncmFkLXVjZ2ctZ2VuZXJhdGVkKSIgLz4KPC9zdmc+);
background: -webkit-gradient(linear, 0 0, 0 100%, from(#27a8e0), to(#1c8ed7));
background: -webkit-linear-gradient(#27a8e0 0%, #1c8ed7 100%);
background: -moz-linear-gradient(#27a8e0 0%, #1c8ed7 100%);
background: -o-linear-gradient(#27a8e0 0%, #1c8ed7 100%);
background: linear-gradient(#27a8e0 0%, #1c8ed7 100%);
box-shadow:  inset    0px 1px 0px   #45c4fc;
filter: progid:DXImageTransform.Microsoft.gradient( startColorstr='#27a8e0', endColorstr='#1c8ed7',GradientType=0 );
-webkit-box-shadow: inset 0px 1px 0px #45c4fc;
-moz-box-shadow: inset  0px 1px 0px  #45c4fc;
-webkit-border-radius:4px;
-moz-border-radius: 4px;
}
.click_logo i {
background: url(https://m.click.uz/static/img/logo.png) no-repeat top left;
width:30px;
height: 25px;
display: block;
float: left;
}
© 2025 Click


Click — Оплата по карте без перехода на страницу оплаты

    Главная
    Click — Оплата по карте без перехода на страницу оплаты

Добавление кнопки оплаты на сайт (1-й вариант)

Добавьте тэг «<script …» внутрь вашей платёжной формы для автоматической генерации кнопки оплатить.
<form method="post" action="/your-after-payment-url">
          <script src="https://my.click.uz/pay/checkout.js"
                    class="uzcard_payment_button"
                    data-service-id="MERCHANT_SERVICE_ID"
                    data-merchant-id="MERCHANT_ID"
                    data-transaction-param="MERCHANT_TRANS_ID"
                    data-merchant-user-id="MERCHANT_USER_ID"
                    data-amount="MERCHANT_TRANS_AMOUNT"
                    data-card-type="MERCHANT_CARD_TYPE"
                    data-label="Оплатить" <!-- Текст кнопки оплаты -->
          ></script>
</form>
# 	Наименование параметра 	Тип данных 	Описание
1 	MERCHANT_ID 	mandatory 	ID поставщика
2 	MERCHANT_USER_ID 	optional 	ID пользователя в системе поставщиков
3 	MERCHANT_SERVICE_ID 	mandatory 	ID Сервиса поставщика
4 	MERCHANT_TRANS_ID 	mandatory 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика. Соответствует merchant_trans_id из SHOP-API
5 	MERCHANT_TRANS_AMOUNT 	mandatory 	Сумма транзакции (формат: N.NN)
6 	MERCHANT_CARD_TYPE 	optional 	Тип платёжной системы (uzcard, humo)

После завершения платежа в окне оплаты, форма будет отправлена на сервер с дополнительным параметром «status».

 
Создание окна оплаты из кода (2-й вариант)

Вызовите метод «createPaymentRequest» который принимает два параметра:

    Объект параметров оплаты
    Callback-фукнция. Вызывается после закрытия окна оплаты. Принимает объект с полем status.

<script src="https://my.click.uz/pay/checkout.js"></script>
<script>
window.onload = function() {
          var linkEl = document.querySelector(".input-btn");
          linkEl.addEventListener("click", function() {
                    createPaymentRequest({
                              service_id: MERCHANT_SERVICE_ID,
                              merchant_id: MERCHANT_ID,
                              amount: MERCHANT_TRANS_AMOUNT,
                              transaction_param: "MERCHANT_TRANS_ID",
                              merchant_user_id: "MERCHANT_USER_ID",
                              card_type: "MERCHANT_CARD_TYPE",
                    }, function(data) {
                              console.log("closed", data.status);
                    });
          });
};
</script>
# 	Наименование параметра 	Тип данных 	Описание
1 	MERCHANT_ID 	mandatory 	ID поставщика
2 	MERCHANT_USER_ID 	optional 	ID пользователя в системе поставщиков
3 	MERCHANT_SERVICE_ID 	mandatory 	ID Сервиса поставщика
4 	MERCHANT_TRANS_ID 	mandatory 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика. Соответствует merchant_trans_id из SHOP-API
5 	MERCHANT_TRANS_AMOUNT 	mandatory 	Сумма транзакции (формат: N.NN)
6 	MERCHANT_CARD_TYPE 	optional 	Тип платёжной системы (uzcard, humo)

 

Возможные значения поля «status»:

    status < 0 — Ошибка
    status = 0 — Платёж создан
    status = 1 — Платёж находится в обработке
    status = 2 — Платёж успешно проведён


Merchant – Общее

    Главная
    Merchant – Общее

Общее положение

Интерфейс CLICK-API-MERCHANT-INVOICE предназначен для выставления счета Пользователю на оплату за товары и услуги со стороны Поставщика посредством сети Интернет, проверки статуса выставленного счета и отмены платежа.
Термины и определения

Система CLICK – система, позволяющая производить оплату посредством мобильного телефона (через USSD\SMS-портал) или интернета (через WEB\WEB-mobile) за услуги сотовых операторов, интернет-провайдеров; переводить денежные средства другим физическим лицам, торгово-сервисным предприятиям; делать онлайн-покупки в интернет-магазинах и др. со счетов, открытых в банке.

Поставщик – юридическое лицо, реализующее товары или услуги пользователям.

Пользователь – физическое лицо, подключенное к системе CLICK.

Сервисы поставщика – объекты реализации поставщика. Сервисами могут являться услуги и товары.


Merchant – Запросы

    Главная
    Merchant – Запросы

Подключение и выполнение запросов
API Точка (Endpoint)

https://api.click.uz/v2/merchant/
Конфиденциальные данные

При регистрации поставщик услуг получает следующие данные для подключения и отправки запросов к API:

    merchant_id
    service_id
    merchant_user_id
    secret_key

secret_key является конфиденциальным параметром и поставщик услуг несет полную ответственность за его безопасность.
Оставив secret_key незащищенным, вы может скомпрометировать ваши данные.
Аутентификация

HTTP Header “Auth: merchant_user_id:digest:timestamp”
digest — sha1(timestamp + secret_key)
timestamp — UNIX timestamp (10 digit seconds from epoch start)
Требуемые заголовки

Accept
Auth
Content-Type
Поддерживаемые виды контента

application/json
application/xml

 
Создать инвойс (счет-фактуру)
Запрос
POST https://api.click.uz/v2/merchant/invoice/create HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543

{
“service_id”: :id сервиса,
“amount”: :сумма,
“phone_number”: :номер телефона,
“merchant_trans_id”: :параметр оплаты
}
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	amount 	float 	Сумма платежа
3 	phone_number 	string 	Получатель инвойса
4 	merchant_trans_id 	string 	ID заказа(для Интернет магазинов)/лицевого счета/логина в биллинге поставщика
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“invoice_id”: 1234567
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	invoice_id 	bigint 	ID инвойса

 
Проверка статуса инвойса (счет-фактуры)
Запрос
GET https://api.click.uz/v2/merchant/invoice/status/:service_id/:invoice_id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“invoice_status”: -99,
“invoice_status_note”: “Удален”,
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	invoice_status 	bigint 	Код статуса инвойса
4 	invoice_status_note 	string 	Описание статуса инвойса

 
Проверка статуса платежа
Запрос
GET https://api.click.uz/v2/merchant/payment/status/:service_id/:payment_id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	bigint 	ID платежа
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“payment_id”: 1234567,
“payment_status”: 1
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
4 	payment_status 	integer 	Код статуса платежа

 
Проверка статуса платежа c помощью merchant_trans_id
Запрос
GET https://api.click.uz/v2/merchant/payment/status_by_mti/:service_id/:merchant_trans_id/YYYY-MM-DD HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:15190515
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	merchant_trans_id 	string 	Идентификатор поставщика
3 	YYYY-MM-DD 	string 	День когда платеж был создан
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: error_code,
“error_note”: “Error description”,
“payment_id”: 1234567,
“merchant_trans_id”: “user123”
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
4 	payment_status 	int 	Код статуса платежа

 
Снятие платежа (отмена)
Запрос
DELETE https://api.click.uz/v2/merchant/payment/reversal/:service_id/:payment:id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	bigint 	Payment ID
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“payment_id”: 1234567
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
Условия снятия (отмены) платежа

    Оплата должна быть успешно завершена
    Только платежи, созданные в текущем отчетном месяце, могут быть возвращены
    Выплаты из предыдущего месяца могут быть отменены только в первый день текущего месяца. Оплата должна производиться с помощью онлайн-карты.
    Отмена платежа может быть отклонена из-за отказа UZCARD

Создание токена карты
Запрос
POST https://api.click.uz/v2/merchant/card_token/request HTTP/1.1
Accept: application/json
Content-Type: application/json

{
“service_id”: :id сервиса,
“card_number”: :номер карты,
“expire_date”: :годен до, // (MMYY)
“temporary”: 1 // (0|1)
}

temporary — создать токен для единичного использования.
Временные токены автоматически удаляются после оплаты.
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	card_number 	string 	Номер карты
3 	expire_date 	string 	Card expire date
4 	temporary 	bit 	Create temporary card
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“card_token”: “3B1DF3F1-7358-407C-B57F-0F6351310803”,
“phone_number”: “99890***1234”,
“temporary”: 1,
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	card_token 	string 	Токен карты
4 	phone_number 	string 	User phone number
4 	temporary 	bit 	Type of created token

 
Подтверждение токена карты
Запрос
POST https://api.click.uz/v2/merchant/card_token/verify HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543

{
“service_id”: :id сервиса,
“card_token”: :токен карты,
“sms_code”: :код смс
}
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	card_token 	string 	токен карты
3 	sms_code 	int 	Полученный смс код
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“card_number”: “8600 55** **** 3244”,
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	card_number 	string 	Номер карты

 
Оплата с помощью токена
Запрос
POST https://api.click.uz/v2/merchant/card_token/payment HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543

{
“service_id”: :id сервиса,
“card_token”: :card_token,
“amount”: :amount,
“transaction_parameter”: :merchant_trans_id
}

transaction_parameter — пользователь или идентификатор контракта при выставлении счетов продавца
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	card_token 	string 	Токен карты
2 	amount 	float 	Сумма платежа
3 	merchant_trans_id 	string 	Номер транзакции
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“payment_id”: “598761234”,
“payment_status”: 1
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
4 	payment_status 	int 	Код статуса платежа

 
Удаление токена карты
Запрос
DELETE https://api.click.uz/v2/merchant/card_token/:service_id/:card_token HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	card_token 	string 	токен карты
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Error description”
}
© 2025 Click


Merchant – Ошибки

    Главная
    Merchant – Ошибки

Errors

Аутентификация и другие ошибки, связанные с API, возвращаются с использованием кодов состояния HTTP.
# 	error 	Описание
1 	200 	OK
2 	201 	OK
3 	400 	Bad request (плохой, неверный запрос)
4 	401 	Not Authorized (не авторизован)
5 	403 	 Forbidden (запрещено)
6 	404 	Not Found (не найдено)
7 	406 	Not Acceptable (неприемлемо)
8 	410 	Gone (неприемлемо)
9 	500 	 Internal Server Error (внутренняя ошибка сервера)
10 	502 	 Service is down or being upgraded.
© 2025 Click


CLICK Pass

    Главная
    CLICK Pass

API Точка (Endpoint)

https://api.click.uz/v2/merchant/
Конфиденциальные данные

При регистрации поставщик услуг получает следующие данные для подключения и отправки запросов к API:

    merchant_id
    service_id
    merchant_user_id
    secret_key

secret_key является конфиденциальным параметром и поставщик услуг несет полную ответственность за его безопасность.
Оставив secret_key незащищенным, вы может скомпрометировать ваши данные.
Аутентификация

HTTP Header “Auth: merchant_user_id:digest:timestamp”
digest — sha1(timestamp + secret_key)
timestamp — UNIX timestamp (10 digit seconds from epoch start)
Требуемые заголовки

Accept
Auth
Content-Type
Поддерживаемые виды контента

application/json
application/xml
Коды статуса платежа
# 	Error code 	Описание
1 	<0 	Ошибка (детали в error_note)
2 	0 	Платеж создан
3 	1 	Обработка
4 	2 	Успешная оплата

 
Оплата с помощью CLICK Pass
Запрос
POST https://api.click.uz/v2/merchant/click_pass/payment HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
{

“service_id”: :id сервиса,
“otp_data”: “1234567415821”,
“amount”: 500,
“cashbox_code”: “KASSA-1”,
“transaction_id”: “12345”

}
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	otp_data 	string 	Содержание QR-кода
3 	amount 	float 	Сумма платежа
4 	cashbox_code 	String (optional) 	Идентификатор кассы
5 	transaction_id 	String (optional) 	ID транзакции поставщика
Ответ
HTTP/1.1 200 OK
Content-Type: application/json
{

“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“payment_id”: 1234567,
“payment_status”: 1,
“confirm_mode”: 1,
«card_type»: «private»,
«processing_type»: «UZCARD»,
«card_number»:»860002******8331″,
«phone_number»:»998221234567″

}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
4 	payment_status 	int 	Статус оплаты (платежа)
5 	confirm_mode 	bit 	Статус режима подтверждения
5 	card_type 	string 	Тип карты

    private
    corporate

5 	processing_type 	string 	Процессинг карты

    UZCARD
    HUMO
    WALLET (Кошелек Click)

6 	card_number 	string 	Номер карты (максированный)
7 	phone_number 	string 	Номер телефона

 
Проверка статуса платежа
Запрос
GET https://api.click.uz/v2/merchant/payment/status/:service_id/:payment_id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	bigint 	Payment ID
Ответ
HTTP/1.1 200 OK
Content-Type: application/json
{

“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“payment_id”: 1234567,
“payment_status”: 1

}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
4 	payment_status 	int 	Код статуса платежа

 
Снятие платежа (отмена)
Запрос
DELETE https://api.click.uz/v2/merchant/payment/reversal/:service_id/:payment:id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	bigint 	Payment ID
Ответ
HTTP/1.1 200 OK
Content-Type: application/json
{

“error_code”: код ошибки,
“error_note”: “Описание ошибки”,
“payment_id”: 1234567

}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
3 	payment_id 	bigint 	ID платежа
Условия снятия (отмены) платежа

    Оплата должна быть успешно завершена
    Только платежи, созданные в текущем отчетном месяце, могут быть возвращены
    Выплаты из предыдущего месяца могут быть отменены только в первый день текущего месяца. Оплата должна производиться с помощью онлайн-карты.
    Отмена платежа может быть отклонена из-за отказа UZCARD

 
Режим подтверждения

    Режим подтверждения включается для сервиса (service_id) и все платежи по CLICK Pass по данному сервису будут работать в режиме подтверждения.
    Платежи работающие в режиме подтверждения должны быть подтверждены сразу после получения успешного ответа на платеж.
    Неподтвержденные платежи будут отменены после 30 секунд после создания платежа.

 
Подтверждение оплаты
Запрос
POST https://api.click.uz/v2/merchant/click_pass/confirm HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
{

“service_id”: :service_id,
“payment_id”: 1234567

}
Ответ
HTTP/1.1 200 OK
Content-Type: application/json
{

“error_code”: 0,
“error_note”: “Платеж подтвержден”

}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки

 
Включение режима подтверждения
Запрос
PUT https://api.click.uz/v2/merchant/click_pass/confirmation/:service_id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Ответ
HTTP/1.1 200 OK
Content-Type: application/json
{

“error_code”: 0,
“error_note”: “Режим подтверждения включен”

}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки

 
Отключение режима подтверждения
Запрос
DELETE https://api.click.uz/v2/merchant/click_pass/confirmation/:service_id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Ответ
HTTP/1.1 200 OK
Content-Type: application/json
{

“error_code”: 0,
“error_note”: “Режим подтверждения выключен”

}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
© 2025 Click


Фискализация данных

    Главная
    Фискализация данных

Фискализация товаров и услуг
Запрос

POST https://api.click.uz/v2/merchant/payment/ofd_data/submit_items HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
{
“service_id”: :id сервиса,
“payment_id”: id платежа CLICK,
“items”: список товаров и услуг,
“received_ecash”: :сумма электронными наличными,
“received_cash”: :сумма наличными,
“received_card”: :сумма безналичными
}
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	long 	ID платежа
3 	items 	Item 	Список товаров и услуг (
4 	received_ecash 	integer 	Полученная от покупателя сумма электронными наличными в тийин
5 	received_cash 	integer 	Полученная от покупателя сумма наличными в тийин
6 	received_card 	integer 	Сумма безналичных полученные от покупателя в тийин

Параметр items — обязательный и должен содержать как минимум одну позицию товара или услуги

Item:
Название поля 	Тип 	Описание
Name 	* string(63) 	Название товара/услуги с ед.изм в конце
Barcode 	string(13) 	Штрихкод
Labels 	[300]string(21) 	Массив кодов маркировок (макс. 300 элементов)
SPIC 	* string(17) 	Код ИКПУ
Units 	uint64 	Код ед.изм
PackageCode 	* string(20) 	Код упаковки
GoodPrice  	uint64 	Цена одного товара/услуги
Price 	* uint64 	Общая сумма позиции с учётом количества и без учёта скидок (тийин)
Amount 	* uint64 	Количество
VAT 	* uint64 	НДС сумма (тийин)
VATPercent  	* int 	НДС %
Discount 	uint64 	Скидка
Other 	uint64 	Прочая скидка (Оплата по страховки и др.)
CommissionInfo  	* CommissionInfo 	Информация о комиссионном чеке

Поля помеченные * — обязательные

CommissionInfo
Название поля  	Тип  	Описание
TIN 	string(9) 	ИНН
PINFL 	string(14) 	ПИНФЛ

CommissionInfo должен содержать ИНН либо ПИНФЛ
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки
Отправка фискализированного чека
Запрос

POST https://api.click.uz/v2/merchant/payment/ofd_data/submit_qrcode HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
{
“service_id”: id сервиса,
“payment_id”: id платежа CLICK,
“qrcode”: «https://ofd.soliq.uz/epi?t=EZ000000000030&r=123456789&c=20221028171340&s=854971301623»
}
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	long 	ID платежа
3 	qrcode 	string 	Ссылка на чек
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
“error_code”: код ошибки,
“error_note”: “Описание ошибки”
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	error_code 	integer 	Код ошибки
2 	error_note 	string 	Описание ошибки

 
Получение фискальных данных (ссылки)
Запрос
GET https://api.click.uz/v2/merchant/payment/ofd_data/:service_id/:payment_id HTTP/1.1
Accept: application/json
Content-Type: application/json
Auth: 123:356a192b7913b04c54574d18c28d46e6395428ab:1519051543
Параметры запроса
# 	Наименование параметра 	Тип данных 	Описание
1 	service_id 	integer 	ID сервиса
2 	payment_id 	long 	ID платежа
Ответ
HTTP/1.1 200 OK
Content-Type: application/json

{
«paymentId»: 1946296773,
«qrCodeURL»: «https://ofd.soliq.uz/epi?t=EZ000000000030&r=123456789&c=20221028171340&s=854971301623»
}
Параметры ответа
# 	Наименование параметра 	Тип данных 	Описание
1 	paymentId 	long 	ID платежа
2 	qrCodeURL 	string 	Ссылка на чек
© 2025 Click


Telegram Payments

    Главная
    Telegram Payments

Создание нового бота

Чтобы создать нового Telegram бота нам поможет официальный бот под названием @BotFather. Чтобы начать «общение» с BotFather находим его, набрав в строке «Поиск» («Search») @BotFather

Далее надо перейти в бот и нажать кнопку Start. После этого появится список команд

Название бота

Для создания своего бота, отправляем команду /newbot – BotFather попросит дать название вашему боту. В названии бота можно использовать только латинские буквы и цифры. Между буквами и цифрами допускается оставлять пробелы.

Имя бота

Имя бота – это адрес вашего бота. Используя его пользователи будут находить вашего бота. Придумайте короткое и запоминающееся имя боту. Имя всегда должно заканчиваться словом bot. Использовать пробелы нельзя. Если кто-то уже использует это имя, Вам предложат придумать другое имя. После этого @BotFather сгенерирует токен – ID для Bot API

Подключение бота к «CLICK Terminal»

Для получения токена отправьте команду /mybots в чате @BotFather и выберите нужного бота (которого вы хотите использовать для продажи своих товаров и услуг) чтобы подключить к CLICK Terminal. В данной инструкции это будет @merchant_test_bot

Перейдите в Настройки бота > Платежи (Settings > Payments).

В списке платежных систем выберите CLICK Uzbekistan, после этого @BotFather предложит вам выбрать «тестовое» (CLICK Terminal Test) или «live» (CLICK Terminal Live) подключение.

Рекомендуем начать с тестового режима, который позволит понять принцип подключения.

Чтобы попробовать тестовый режим выберите «Connect CLICK Terminal Test», и вы будете перенаправлены на наш тестовый бот — @CLICKtest

После того как нажмёте на кнопку Start выйдет Кнопка с текстом «Авторизоваться»

Нажимаете на «Авторизоваться» и будете перенаправлены обратно на @BotFather и теперь вам будут показаны доступные платежные системы. У каждого будет имя, токен и дата подключения. Вы будете использовать токен при работе с Bot API.

Для подключения к LIVE системе выберите «Connect CLICK Terminal Live», и вы будете перенаправлены на бот — @CLICKTerminal

После того как нажмёте на кнопку Start выйдет Кнопка с текстом «Авторизоваться»

Нажмите на кнопку «Авторизоваться» и откроется форма авторизации в веб-браузере. В данном случае вам необходимо зайти в кабинет поставщика используя свой логин/пароль и затем выбрать сервис. После того как вы выбрали сервис, вы будете перенаправлены обратно на @BotFather и теперь вам будут показаны доступные платежные системы. У каждого будет имя, токен и дата подключения. Вы будете использовать токен при работе с Bot API

 
Заключение договора

Для приема оплаты через CLICK Поставщику необходимо быть оформленным как юридическое лицо, либо быть индивидуальным предпринимателем. Поставщику необходимо заключить договор на прием платежей с одним из банком, подключенных к системе («Алокабанк», «Агро банк», «Давр банк», «Узпромстройбанк», «Кишлок Курилиш банк», «Узбекско-Турецкий банк», «Универсал банк», «Савдогар банк», «Траст банк», «Туркистон банк», «Халк банк», «Микрокредит банк», «Ориент Финанс банк», «Asia Alliance Bank», «Ипак Йули банк»)*. Абонентской платы или платы за подключения к системе не взимается. Поставщик оплачивает банку лишь вознаграждение за прием платежей (в % от оборота, оплаченных через систему CLICK).

Поставщик самостоятельно оплачивает вознаграждение Банку по итогам месяца, согласно полученной от него счет-фактуры. Исключением является некоторые банки, которые сами удерживают вознаграждение (комиссию). Документы, необходимые для подключения к системе (копии)**:

    Свидетельство о регистрации
    Лицензия (если деятельность подлежит обязательному лицензированию)
    Приказ о назначении директора
    Паспорт директора
    Протокол собрания учредителей о назначении директора
    Устав (все страницы)
    Договор на домен
    Письмо о подключении (только для АКБ «Asia Alliance Bank»)

* Список банков постоянно расширяется, подробную информацию о подключенных банках можете узнать у менеджера.

** В зависимости от банка список документов может незначительно меняться, подробности можно получить у менеджера

Телефон для справок: +998 (71) 231-08-83

 
Тестирование приема оплаты

После того как вы получили токен, можно приступать к основной работе. Процесс оплаты состоит из нескольких шагов:

1. Создать счет

Пользователь, который хочет купить что-то у поставщика пишет боту поставщика, затем выбирает товары или услуги. Бот поставщика формирует счет с описанием товаров или услуг, сумму для оплаты, а также информаций о доставке.

Для формирования счета используется метод sendInvoice. В параметре provider_token надо указать токен, который вы получили через @BotFather. Один бот может использовать несколько разных токенов для разных пользователей или товаров и услуг.

Счет с кнопкой оплаты можно отправить только пользователю, который написал боту. Группам и каналам счета отправлять нельзя. Сформированный счет будет выглядеть так:

2. Проверка и подтверждение заказа

После того как пользователь вводит нужную информацию и нажимает кнопку «Pay», Bot API отправляет Update с полем pre_checkout_query, который содержит в себе всю доступную информацию о заказе. Ваш бот должен в течении 10 секунд ответить с помощью answerPrecheckoutQuery после получения Update с pre_checkout_query или отменить транзакцию

Если бот не смог обработать заказ по какой-либо причине, он возвращает ошибку. Рекомендуется написать текст ошибки так, чтобы было понятно обычному пользователю. Например, «Извините, этого товара нет в наличии, может Вас интересуют другие наши товары?»

3. Оплата

После того как бот поставщика подтверждает заказ, Telegram попросит платежную систему завершить транзакцию. Если оплата пройдет успешно Bot API отправит сообщение типа success_payment от пользователя

Дальше пользователю показывается чек в виде квитанции. Он может в любой момент открыть эту квитанцию и посмотреть детали платежа.

Переключиться на «LIVE» режим

Если вы уже все тщательно проверили и убедились что платежи работают для вашего бота, вы можете переключиться на «LIVE» режим. Для этого перейдите в @BotFather. Отправьте команду /mybots и выберите нужнего бота

Дальше Настройки бота > Платежи (Settings > Payments) > CLICK Uzbekistan и выбераем Connect CLICK Terminal LIVE и вы будете перенаправлены на бот @CLICKTerminal

После того как нажмeте на кнопку Start выйдет Кнопка с текстом «Авторизоваться»

Нажмите на кнопку «Авторизоваться»

Логин и пароль выдаётся банком, после заключения договора

Откроется форма авторизации в веб-браузере. В данном случае вам необходимо зайти в кабинет поставщика используя свой логин / пароль и затем выбрать сервис. После того как вы выбрали сервис, вы будете перенаправлены обратно на @BotFather и получите токен который содержить в себе строку :LIVE: посередине, например 123:LIVE:XXXX. Не давайте этот токен третьим лицам!

 

Прежде чем ваш бот перейдет на «LIVE» режим, убедитесь, что:

    Настоятельно рекомендуется включить двухэтапную аутентификацию для учетной записи Telegram, которая управляет вашим ботом.
    Вы, как владелец бота, несете полную ответственность в случае возникновения конфликтов или споров. Вы должны быть готовы правильно обрабатывать споры и возвраты
    Чтобы предотвратить любые недоразумения и возможные юридические проблемы, убедитесь, что ваш бот может ответить на команду /terms. Ваши Условия и положения должны быть четко и понятны для ваших пользователей. Пользователи должны подтвердить, что они прочитали и согласны с условиями, прежде чем совершить покупку.
    Ваш бот должен оказывать поддержку своим клиентам, либо реагируя на команду /support, либо на некоторые другие четко переданные средства. Пользователи должны иметь четкий способ связаться с вами о своих покупках, и вы должны своевременно обрабатывать их запросы на поддержку. Вы должны уведомить своих пользователей о том, что поддержка Telegram или поддержка ботов не помогут им с покупками, сделанными через вашего бота.
    Убедитесь, что ваше серверное оборудование и программное обеспечение стабильны. Используйте резервные копии, чтобы не потерять данные о платежах пользователей.

© 2025 Click


Плагины для CMS

    Главная
    Плагины для CMS

Плагин для WordPress
с версии 3.5 и выше
Плагин для Drupal
с версии 7.x-3.11 и выше
Плагин для Opencart
для версии 3.x
Плагин для 1C-Bitrix
для версии 20.x
Плагин для Joomla!
для версии 3.x
Плагин для CS-Cart
для версии 4.5.x
© 2025 Click


Пример реализации сервера

    Главная
    Пример реализации сервера

Пример реализации сервера на PHP

Пример реализации и детальная документация доступны по ссылке https://github.com/click-llc/click-integration-php
Пример реализации сервера на Python Django

Пример реализации и детальная документация доступны по ссылке https://github.com/click-llc/click-integration-django
© 2025 Click


Интеграция с мобильным приложением

    Главная
    Интеграция с мобильным приложением

Интеграция с мобильными приложениями на Android и iOS

Наше мобильное приложение на обоих платформах перехватывает ссылки (deeplink) для оплаты. Здесь показано как создается ссылка. Если приложение не установлено на телефоне пользователя, то на системном уровне откроется браузер и уже там на сайте пользователь может оплатить.
Пример кода Android (Kotlin)
val url =
"https://my.click.uz/services/pay/?service_id=${it.merchantServiceId}&merchant_id=${it.merchantId}&amount=${it.merchantTransAmount}&transaction_param=${it.merchantTransId}"
val i = Intent(Intent.ACTION_VIEW)
i.data = Uri.parse(url)
startActivity(i)


Как работает Click Evolution

После успешной оплаты наше приложение просто закрывает себя и вы в вашем коде, методе onStart или onRestart можете обновить данные из вашего сервера по transaction_param который вы ранее использовали.

Если вы как параметр к ссылке добавили return_url, то после оплаты (даже после ошибки) будет вызываться в нашем приложении эта ссылка как Intent.ACTION_VIEW и закроет само себя. Вы в своем приложении можете слушать (deeplink) return_url привязав его на ваш Activity. Можете так же использовать уникальные схемы. См https://developer.android.com/training/app-links/deep-linking


Пример кода в iOS (Swift)
guard let url = URL(string: "https://my.click.uz/services/pay/?service_id=\(merchantServiceId)&merchant_id=\(merchantId)&amount=\(merchantTransAmount)&transaction_param=\(merchantTransId)") else { return }
UIApplication.shared.open(url)


Интеграция с мобильным приложением на Android

Для интеграции платежной системы «CLICK» с мобильным приложением, подключите к мобильному приложению библиотеку Click Mobile SDK. Библиотеку можно использовать для подключения как поставщиков с биллингом, так и без биллинга. Для поставщиков с биллингом нужно реализовать SHOP-API на сервере приложения.

Библиотека и детальная документация доступны по ссылке https://github.com/click-llc/android-msdk
