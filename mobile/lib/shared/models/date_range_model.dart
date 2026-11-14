import 'package:intl/intl.dart';

class DateRangeModel {
  final DateTime startDate;
  final DateTime endDate;

  const DateRangeModel({required this.startDate, required this.endDate});

  int get durationInDays => endDate.difference(startDate).inDays + 1;

  static String formatDateRange(DateRangeModel dateRange) {
    final formatter = DateFormat('MMM d');
    final start = formatter.format(dateRange.startDate);
    final end = DateFormat('MMM d').format(dateRange.endDate);
    return '$start - $end';
  }
}
