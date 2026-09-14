import 'package:flutter/foundation.dart';

import '../models/customer.dart';
import '../repositories/customer_repository.dart';

/// Holds the customer list UI state and exposes validation + persistence.
class CustomersController extends ChangeNotifier {
  CustomersController(this._repository);

  final CustomerRepository _repository;

  List<Customer> _customers = [];
  bool _isLoading = false;

  List<Customer> get customers => _customers;
  bool get isLoading => _isLoading;

  Future<void> load() async {
    _isLoading = true;
    notifyListeners();
    try {
      _customers = await _repository.getAll();
    } catch (error) {
      debugPrint('Failed to load customers: $error');
      _customers = [];
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  /// Returns `null` when valid, otherwise a validation error message key.
  /// Message keys are resolved to localized text in the view layer.
  String? validate({required String name, String? phone}) {
    if (name.trim().isEmpty) return 'nameRequired';
    if (phone != null && phone.trim().isNotEmpty) {
      final digits = phone.replaceAll(RegExp(r'[\s\-()]'), '');
      if (digits.length < 7 || digits.length > 15 || !RegExp(r'^[0-9+]+$').hasMatch(digits)) {
        return 'invalidPhone';
      }
    }
    return null;
  }

  Future<Customer?> save({
    int? id,
    required String name,
    String? phone,
    String? notes,
  }) async {
    try {
      final customer = Customer(
        id: id,
        name: name.trim(),
        phone: phone?.trim().isEmpty ?? true ? null : phone!.trim(),
        notes: notes?.trim().isEmpty ?? true ? null : notes!.trim(),
      );
      if (id == null) {
        await _repository.create(customer);
      } else {
        await _repository.update(customer);
      }
      await load();
      return customer;
    } catch (error) {
      debugPrint('Failed to save customer: $error');
      rethrow;
    }
  }

  Future<void> delete(Customer customer) async {
    await _repository.delete(customer.id!);
    await load();
  }
}
