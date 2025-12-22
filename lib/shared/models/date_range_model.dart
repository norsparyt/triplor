class DateRangeModel {
  final DateTime startDate;
  final DateTime endDate;

  const DateRangeModel({
    required this.startDate,
    required this.endDate,
  });

  int get durationInDays =>
      endDate.difference(startDate).inDays + 1;
}