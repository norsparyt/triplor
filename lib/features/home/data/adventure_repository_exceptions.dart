class AdventureNotFoundException implements Exception {
  final String adventureId;

  AdventureNotFoundException(this.adventureId);

  @override
  String toString() =>
      'AdventureNotFoundException: Adventure with id $adventureId not found';
}
