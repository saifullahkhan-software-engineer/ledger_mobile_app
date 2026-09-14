import 'dart:ui';

import 'package:flutter/widgets.dart';

/// Simple, dependency-free localization for English and Urdu.
///
/// The app ships exactly two locales (en LTR, ur RTL). Adding a third language
/// is a matter of adding another `_LocalizedStrings` implementation and
/// resolving it in [AppL10n.delegate].
class AppL10n {
  const AppL10n(this._strings);

  static const LocalizationsDelegate<AppL10n> delegate = _AppL10nDelegate();

  final _LocalizedStrings _strings;

  static AppL10n of(BuildContext context) =>
      Localizations.of<AppL10n>(context, AppL10n)!;

  String get appTitle => _strings.appTitle;
  String get customers => _strings.customers;
  String get addCustomer => _strings.addCustomer;
  String get editCustomer => _strings.editCustomer;
  String get customerName => _strings.customerName;
  String get phone => _strings.phone;
  String get optional => _strings.optional;
  String get notes => _strings.notes;
  String get save => _strings.save;
  String get cancel => _strings.cancel;
  String get delete => _strings.delete;
  String get nameRequired => _strings.nameRequired;
  String get invalidPhone => _strings.invalidPhone;
  String get customerSaved => _strings.customerSaved;
  String get customerDeleted => _strings.customerDeleted;
  String get noCustomers => _strings.noCustomers;
  String get noCustomersHint => _strings.noCustomersHint;
  String get confirmDeleteCustomerTitle => _strings.confirmDeleteCustomerTitle;
  String get confirmDeleteCustomerBody => _strings.confirmDeleteCustomerBody;
  String get addCredit => _strings.addCredit;
  String get receivePayment => _strings.receivePayment;
  String get amount => _strings.amount;
  String get description => _strings.description;
  String get date => _strings.date;
  String get today => _strings.today;
  String get items => _strings.items;
  String get itemName => _strings.itemName;
  String get quantity => _strings.quantity;
  String get unit => _strings.unit;
  String get price => _strings.price;
  String get addItem => _strings.addItem;
  String get removeItem => _strings.removeItem;
  String get itemsHint => _strings.itemsHint;
  String get amountRequired => _strings.amountRequired;
  String get amountInvalid => _strings.amountInvalid;
  String get itemFieldsRequired => _strings.itemFieldsRequired;
  String get itemPriceInvalid => _strings.itemPriceInvalid;
  String get itemQuantityInvalid => _strings.itemQuantityInvalid;
  String get itemAmountInvalid => _strings.itemAmountInvalid;
  String get entrySaved => _strings.entrySaved;
  String get entrySavedCredit => _strings.entrySavedCredit;
  String get entrySavedPayment => _strings.entrySavedPayment;
  String get transactionHistory => _strings.transactionHistory;
  String get noEntries => _strings.noEntries;
  String get noEntriesHint => _strings.noEntriesHint;
  String get credit => _strings.credit;
  String get payment => _strings.payment;
  String get balance => _strings.balance;
  String get currentBalance => _strings.currentBalance;
  String get totalCredit => _strings.totalCredit;
  String get totalPayments => _strings.totalPayments;
  String get receive => _strings.receive;
  String get language => _strings.language;
  String get error => _strings.error;
  String get ok => _strings.ok;
  String get confirm => _strings.confirm;
  String get tryAgain => _strings.tryAgain;
  String get loading => _strings.loading;
  String get unpaid => _strings.unpaid;
  String get settled => _strings.settled;

  String customerBalance(int minorUnits) =>
      _strings.customerBalance(formatAmount(minorUnits));

  String confirmationText(int minorUnits) =>
      _strings.confirmationText(formatAmount(minorUnits));

  String _format(int minorUnits) {
    final sign = minorUnits < 0 ? '-' : '';
    final abs = minorUnits.abs();
    final major = abs ~/ 100;
    final minor = abs % 100;
    return '$sign$major.${minor.toString().padLeft(2, '0')}';
  }

  String formatAmount(int minorUnits) => _format(minorUnits);
}

class _LocalizedStrings {
  final String appTitle;
  final String customers;
  final String addCustomer;
  final String editCustomer;
  final String customerName;
  final String phone;
  final String optional;
  final String notes;
  final String save;
  final String cancel;
  final String delete;
  final String nameRequired;
  final String invalidPhone;
  final String customerSaved;
  final String customerDeleted;
  final String noCustomers;
  final String noCustomersHint;
  final String confirmDeleteCustomerTitle;
  final String confirmDeleteCustomerBody;
  final String addCredit;
  final String receivePayment;
  final String amount;
  final String description;
  final String date;
  final String today;
  final String items;
  final String itemName;
  final String quantity;
  final String unit;
  final String price;
  final String addItem;
  final String removeItem;
  final String itemsHint;
  final String amountRequired;
  final String amountInvalid;
  final String itemFieldsRequired;
  final String itemPriceInvalid;
  final String itemQuantityInvalid;
  final String itemAmountInvalid;
  final String entrySaved;
  final String entrySavedCredit;
  final String entrySavedPayment;
  final String transactionHistory;
  final String noEntries;
  final String noEntriesHint;
  final String credit;
  final String payment;
  final String balance;
  final String currentBalance;
  final String totalCredit;
  final String totalPayments;
  final String receive;
  final String language;
  final String error;
  final String ok;
  final String confirm;
  final String tryAgain;
  final String loading;
  final String unpaid;
  final String settled;
  final String Function(String amount) customerBalance;
  final String Function(String amount) confirmationText;

  const _LocalizedStrings({
    required this.appTitle,
    required this.customers,
    required this.addCustomer,
    required this.editCustomer,
    required this.customerName,
    required this.phone,
    required this.optional,
    required this.notes,
    required this.save,
    required this.cancel,
    required this.delete,
    required this.nameRequired,
    required this.invalidPhone,
    required this.customerSaved,
    required this.customerDeleted,
    required this.noCustomers,
    required this.noCustomersHint,
    required this.confirmDeleteCustomerTitle,
    required this.confirmDeleteCustomerBody,
    required this.addCredit,
    required this.receivePayment,
    required this.amount,
    required this.description,
    required this.date,
    required this.today,
    required this.items,
    required this.itemName,
    required this.quantity,
    required this.unit,
    required this.price,
    required this.addItem,
    required this.removeItem,
    required this.itemsHint,
    required this.amountRequired,
    required this.amountInvalid,
    required this.itemFieldsRequired,
    required this.itemPriceInvalid,
    required this.itemQuantityInvalid,
    required this.itemAmountInvalid,
    required this.entrySaved,
    required this.entrySavedCredit,
    required this.entrySavedPayment,
    required this.transactionHistory,
    required this.noEntries,
    required this.noEntriesHint,
    required this.credit,
    required this.payment,
    required this.balance,
    required this.currentBalance,
    required this.totalCredit,
    required this.totalPayments,
    required this.receive,
    required this.language,
    required this.error,
    required this.ok,
    required this.confirm,
    required this.tryAgain,
    required this.loading,
    required this.unpaid,
    required this.settled,
    required this.customerBalance,
    required this.confirmationText,
  });
}

class _EnglishStrings extends _LocalizedStrings {
  const _EnglishStrings()
      : super(
          customerBalance: _customerBalance,
          confirmationText: _confirmationText,
          appTitle: 'Ledger',
          customers: 'Customers',
          addCustomer: 'Add customer',
          editCustomer: 'Edit customer',
          customerName: 'Customer name',
          phone: 'Phone',
          optional: 'optional',
          notes: 'Notes',
          save: 'Save',
          cancel: 'Cancel',
          delete: 'Delete',
          nameRequired: 'Please enter a customer name.',
          invalidPhone: 'Please enter a valid phone number.',
          customerSaved: 'Customer saved.',
          customerDeleted: 'Customer deleted.',
          noCustomers: 'No customers yet',
          noCustomersHint: 'Add your first customer to start keeping udhaar.',
          confirmDeleteCustomerTitle: 'Delete customer?',
          confirmDeleteCustomerBody:
              'This will remove the customer and all of their transactions.',
          addCredit: 'Add credit (udhaar)',
          receivePayment: 'Receive payment',
          amount: 'Amount',
          description: 'Description',
          date: 'Date',
          today: 'Today',
          items: 'Items',
          itemName: 'Item name',
          quantity: 'Qty',
          unit: 'Unit',
          price: 'Price',
          addItem: 'Add item',
          removeItem: 'Remove item',
          itemsHint: 'Item details are required for credit, optional for payment.',
          amountRequired: 'Please enter an amount.',
          amountInvalid: 'Please enter a valid amount.',
          itemFieldsRequired: 'Enter item name, quantity, unit and price.',
          itemPriceInvalid: 'Please enter a valid item price.',
          itemQuantityInvalid: 'Please enter a valid quantity.',
          itemAmountInvalid: 'Item amount must be greater than zero.',
          entrySaved: 'Entry saved.',
          entrySavedCredit: 'Credit added.',
          entrySavedPayment: 'Payment received.',
          transactionHistory: 'Transaction history',
          noEntries: 'No transactions yet',
          noEntriesHint: 'Credit and payment entries will appear here.',
          credit: 'Credit',
          payment: 'Payment',
          balance: 'Balance',
          currentBalance: 'Current balance',
          totalCredit: 'Total credit',
          totalPayments: 'Total payments',
          receive: 'Receive',
          language: 'Language',
          error: 'Error',
          ok: 'OK',
          confirm: 'Confirm',
          tryAgain: 'Try again',
          loading: 'Loading…',
          unpaid: 'Unpaid',
          settled: 'Settled',
        );

  static String _customerBalance(String amount) => 'Balance: $amount';

  static String _confirmationText(String amount) => 'Total: $amount';
}

class _UrduStrings extends _LocalizedStrings {
  const _UrduStrings()
      : super(
          customerBalance: _customerBalance,
          confirmationText: _confirmationText,
          appTitle: 'لیجر',
          customers: 'گاہک',
          addCustomer: 'گاہک شامل کریں',
          editCustomer: 'گاہک میں ترمیم',
          customerName: 'گاہک کا نام',
          phone: 'فون',
          optional: 'اختیاری',
          notes: 'نوٹس',
          save: 'محفوظ کریں',
          cancel: 'منسوخ',
          delete: 'حذف کریں',
          nameRequired: 'براہ کرم گاہک کا نام درج کریں۔',
          invalidPhone: 'براہ کرم درست فون نمبر درج کریں۔',
          customerSaved: 'گاہک محفوظ ہو گیا۔',
          customerDeleted: 'گاہک حذف ہو گیا۔',
          noCustomers: 'ابھی کوئی گاہک نہیں',
          noCustomersHint: 'ادھار شروع کرنے کے لیے اپنا پہلا گاہک شامل کریں۔',
          confirmDeleteCustomerTitle: 'گاہک حذف کریں؟',
          confirmDeleteCustomerBody:
              'اس سے گاہک اور اس کے تمام لین دین حذف ہو جائیں گے۔',
          addCredit: 'ادھار شامل کریں',
          receivePayment: 'ادائیگی وصول کریں',
          amount: 'رقم',
          description: 'تفصیل',
          date: 'تاریخ',
          today: 'آج',
          items: 'اشیاء',
          itemName: 'شے کا نام',
          quantity: 'مقدار',
          unit: 'اکائی',
          price: 'قیمت',
          addItem: 'شے شامل کریں',
          removeItem: 'شے ہٹائیں',
          itemsHint: 'ادھار کے لیے شے کی تفصیل لازمی، ادائیگی کے لیے اختیاری۔',
          amountRequired: 'براہ کرم رقم درج کریں۔',
          amountInvalid: 'براہ کرم درست رقم درج کریں۔',
          itemFieldsRequired: 'شے کا نام، مقدار، اکائی اور قیمت درج کریں۔',
          itemPriceInvalid: 'براہ کرم درست قیمت درج کریں۔',
          itemQuantityInvalid: 'براہ کرم درست مقدار درج کریں۔',
          itemAmountInvalid: 'شے کی رقم صفر سے زیادہ ہونی چاہیے۔',
          entrySaved: 'اندراج محفوظ ہو گیا۔',
          entrySavedCredit: 'ادھار شامل ہو گیا۔',
          entrySavedPayment: 'ادائیگی وصول ہو گئی۔',
          transactionHistory: 'لین دین کی تاریخ',
          noEntries: 'ابھی کوئی لین دین نہیں',
          noEntriesHint: 'ادھار اور ادائیگی کے اندراجات یہاں ظاہر ہوں گے۔',
          credit: 'ادھار',
          payment: 'ادائیگی',
          balance: 'بیلنس',
          currentBalance: 'موجودہ بیلنس',
          totalCredit: 'کل ادھار',
          totalPayments: 'کل ادائیگیاں',
          receive: 'وصول',
          language: 'زبان',
          error: 'خرابی',
          ok: 'ٹھیک ہے',
          confirm: 'تصدیق کریں',
          tryAgain: 'دوبارہ کوشش کریں',
          loading: 'لوڈ ہو رہا ہے…',
          unpaid: 'بقایا',
          settled: 'ادا شدہ',
        );

  static String _customerBalance(String amount) => 'بیلنس: $amount';

  static String _confirmationText(String amount) => 'کل رقم: $amount';
}

class _AppL10nDelegate extends LocalizationsDelegate<AppL10n> {
  const _AppL10nDelegate();

  @override
  bool isSupported(Locale locale) =>
      locale.languageCode == 'en' || locale.languageCode == 'ur';

  @override
  Future<AppL10n> load(Locale locale) {
    final AppL10n value =
        locale.languageCode == 'ur' ? const AppL10n(_UrduStrings()) : const AppL10n(_EnglishStrings());
    return SynchronousFuture<AppL10n>(value);
  }

  @override
  bool shouldReload(covariant LocalizationsDelegate<AppL10n> old) => false;
}
