/// The two kinds of ledger entries. Mirrors the `CHECK` constraint on the
/// `entries.type` column.
enum EntryType {
  credit('CREDIT'),
  payment('PAYMENT');

  const EntryType(this.dbValue);

  final String dbValue;

  static EntryType fromDb(String? value) {
    return EntryType.values.firstWhere(
      (type) => type.dbValue == value,
      orElse: () => EntryType.credit,
    );
  }
}
