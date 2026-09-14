/// A customer with an udhaar account.
class Customer {
  const Customer({
    this.id,
    required this.name,
    this.phone,
    this.notes,
    this.balance = 0,
  });

  final int? id;
  final String name;
  final String? phone;
  final String? notes;

  /// Net outstanding balance in minor units. `0`/positive = the customer owes
  /// this much; negative = the shop owes the customer.
  final int balance;

  bool get isSettled => balance <= 0;

  Customer copyWith({
    int? id,
    String? name,
    String? phone,
    String? notes,
    int? balance,
  }) {
    return Customer(
      id: id ?? this.id,
      name: name ?? this.name,
      phone: phone ?? this.phone,
      notes: notes ?? this.notes,
      balance: balance ?? this.balance,
    );
  }

  factory Customer.fromMap(Map<String, Object?> map) {
    return Customer(
      id: map['id'] as int?,
      name: map['name'] as String,
      phone: map['phone'] as String?,
      notes: map['notes'] as String?,
      balance: (map['balance'] as int?) ?? 0,
    );
  }

  Map<String, Object?> toMap() => {
        if (id != null) 'id': id,
        'name': name,
        'phone': phone,
        'notes': notes,
      };
}
