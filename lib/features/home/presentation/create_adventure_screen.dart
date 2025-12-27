import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:triplor/features/home/domain/models/adventure_model.dart';
import 'package:triplor/features/home/providers/create_adventure_state.dart';

import '../../../app/theme/app_colors.dart';
import '../../../core/constants/app_strings.dart';
import '../../../shared/models/date_range_model.dart';
import '../../../shared/models/location_model.dart';
import '../providers/adventure_providers.dart';
import '../providers/create_adventure_form_state.dart';

class CreateAdventureScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<ConsumerStatefulWidget> createState() =>
      _CreateAdventureScreen();
}

class _CreateAdventureScreen extends ConsumerState<CreateAdventureScreen> {
  final _scrollController = ScrollController();
  final _locationController = TextEditingController();
  final _descriptionController = TextEditingController();

  @override
  void dispose() {
    _locationController.dispose();
    _descriptionController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // final screenWidth = MediaQuery.of(context).size.width;
    // final screenHeight = MediaQuery.of(context).size.height;
    final createAdventureState = ref.watch(createAdventureProvider);
    final formState = ref.watch(createAdventureFormProvider);

    return Scaffold(
      backgroundColor: AppColors.globalWhite,
      appBar: AppBar(
        backgroundColor: AppColors.globalWhite,
        elevation: 0,
        leading: TextButton(
          onPressed: () => context.go(AppStrings.homeRoute),
          child: Text(
            AppStrings.cancelCreateAdventure,
            style: TextStyle(color: AppColors.grey700, fontSize: 16),
          ),
        ),
        leadingWidth: 80,
        title: Text(
          AppStrings.createAdventureHeading,
          style: TextStyle(
            color: AppColors.globalBlack,
            fontSize: 18,
            fontWeight: FontWeight.w600,
          ),
        ),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        controller: _scrollController,
        padding: EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Where to?
            _buildSectionTitle(AppStrings.whereTo),
            SizedBox(height: 12),
            _buildLocationField(),

            SizedBox(height: 32),

            // When?
            _buildSectionTitle(AppStrings.when),
            SizedBox(height: 12),
            _buildDateRange(formState),

            SizedBox(height: 32),

            // Adventure Style
            _buildSectionTitle(AppStrings.tripStyle),
            SizedBox(height: 12),

            _buildAdventureTypeChips(formState),
            SizedBox(height: 32),

            // Looking for...
            _buildSectionTitle(AppStrings.lookingFor),
            SizedBox(height: 12),

            _buildLookingForToggle(formState),
            SizedBox(height: 32),

            // Max People
            (formState.isGroupTrip)
                ? _buildSectionTitle(AppStrings.maxPeople)
                : Container(),
            SizedBox(height: 12),

            (formState.isGroupTrip)
                ? _buildPeopleCounter(formState)
                : Container(),
            SizedBox(height: 32),

            // The Plan
            _buildSectionTitle(AppStrings.thePlan),
            SizedBox(height: 12),

            _buildDescriptionField(formState),
            SizedBox(height: 16),

            // Safety message
            _buildSafetyMessage(),
            SizedBox(height: 24),

            // Save button
            _buildSaveButton(formState, createAdventureState),
            SizedBox(height: 40),
          ],
        ),
      ),
    );
  }

  // Functions start here
  void handleCreate() async {
    //get the notifier of the provider (to use the model class's methods etc)
    final notifier = ref.read(createAdventureProvider.notifier);
    final formState = ref.read(createAdventureFormProvider);

    if (!formState.isValid) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Please fill all required fields'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }
    await notifier.createAdventure(
      userId: "",
      // TODO: Get from auth provider in real app
      location: LocationModel(city: formState.location, country: "Default"),
      dateRange: DateRangeModel(
        startDate: formState.startDate!,
        endDate: formState.endDate!,
      ),
      type: formState.selectedTripStyles,
      description: formState.description,
      maxPeople: formState.maxPeople,
      //TODO add parameter: is solo?
    );

    //get the state of the provider
    final state = ref.read(createAdventureProvider);
    if (state.createdAdventure != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppStrings.adventureCreatedSuccess)),
      );
      context.go(AppStrings.homeRoute);
    } else if (state.error != null && mounted) {
      // Error occurred
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${AppStrings.errorPrefix}${state.error}')),
      );
    }

    //NOTE both are read because we dont need to watch any changes.
  }

  // Widgets start here
  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        color: AppColors.globalBlack,
      ),
    );
  }

  Widget _buildLocationField() {
    return TextField(
      controller: _locationController,
      onChanged: (value) {
        ref.read(createAdventureFormProvider.notifier).updateLocation(value);
      },
      decoration: InputDecoration(
        hintText: AppStrings.locationFieldHint,
        hintStyle: TextStyle(color: AppColors.grey400),
        prefixIcon: Icon(
          Icons.location_on,
          color: AppColors.primaryColorGlobal,
        ),
        filled: true,
        fillColor: AppColors.grey50,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: AppColors.grey200),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: AppColors.grey200),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: AppColors.primaryColorGlobal, width: 2),
        ),
      ),
    );
  }

  Widget _buildDateRange(CreateAdventureFormState formState) {
    return Row(
      children: [
        Expanded(
          child: _buildDateButton(
            label: AppStrings.startDate,
            date: formState.startDate,
            onTap: () => _selectDateRange(context),
          ),
        ),
        SizedBox(width: 12),
        Expanded(
          child: _buildDateButton(
            label: AppStrings.endDate,
            date: formState.endDate,
            onTap: () => _selectDateRange(context),
          ),
        ),
      ],
    );
  }

  Future<void> _selectDateRange(BuildContext context) async {
    final initialRange = DateTimeRange(
      start: ref.read(createAdventureFormProvider).startDate ?? DateTime.now(),
      end:
          ref.read(createAdventureFormProvider).endDate ??
          DateTime.now().add(const Duration(days: 7)),
    );

    // Trigger the modal
    final DateTimeRange? picked = await showDateRangePicker(
      context: context,
      initialDateRange: initialRange,
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 365)), // 1 year limit
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: ColorScheme.light(
              primary: AppColors.primaryColorGlobal,
              onPrimary: AppColors.globalWhite,
              onSurface: AppColors.globalBlack,
            ),
          ),
          child: child!,
        );
      },
    );
    if (picked != null) {
      ref
          .read(createAdventureFormProvider.notifier)
          .updateDates(picked.start, picked.end);
    }
  }

  Widget _buildDateButton({
    required String label,
    required DateTime? date,
    required VoidCallback onTap,
  }) {
    final dateText = date != null
        ? DateFormat(AppStrings.dateFormat).format(date)
        : AppStrings.selectDate;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: EdgeInsets.all(16),
        decoration: BoxDecoration(
          //todo
          color: date != null ? AppColors.blueish : AppColors.grey50,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: date != null
                ? AppColors.primaryColorGlobal.withOpacity(0.3)
                : AppColors.grey200,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                color: AppColors.grey600,
                fontWeight: FontWeight.w500,
              ),
            ),
            SizedBox(height: 4),
            Text(
              dateText,
              style: TextStyle(
                fontSize: 18,
                color: date != null
                    ? AppColors.primaryColorGlobal
                    : AppColors.grey400,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAdventureTypeChips(CreateAdventureFormState formState) {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: AdventureType.values.map((style) {
        final isSelected = formState.selectedTripStyles.contains(style);
        return GestureDetector(
          onTap: () {
            ref
                .read(createAdventureFormProvider.notifier)
                .toggleTripStyle(style);
          },
          child: Container(
            padding: EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            decoration: BoxDecoration(
              color: isSelected
                  ? AppColors.primaryColorGlobal
                  : AppColors.globalWhite,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: isSelected
                    ? AppColors.primaryColorGlobal
                    : AppColors.grey300, //to do why
              ),
            ),
            child: Text(
              style.label,
              style: TextStyle(
                color: isSelected ? AppColors.globalWhite : AppColors.grey700,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                fontSize: 15,
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildLookingForToggle(CreateAdventureFormState formState) {
    return Row(
      children: [
        Expanded(
          child: _buildTripTypeCard(
            icon: Icons.person,
            title: AppStrings.travelBuddy,
            subtitle: AppStrings.travelBuddySubtitle,
            isSelected: !formState.isGroupTrip,
            onTap: () {
              ref
                  .read(createAdventureFormProvider.notifier)
                  .toggleTripType(false);
            },
          ),
        ),
        SizedBox(width: 12),
        Expanded(
          child: _buildTripTypeCard(
            icon: Icons.group,
            title: AppStrings.groupTrip,
            subtitle: AppStrings.groupTripSubtitle,
            isSelected: formState.isGroupTrip,
            onTap: () {
              ref
                  .read(createAdventureFormProvider.notifier)
                  .toggleTripType(true);
            },
          ),
        ),
      ],
    );
  }

  Widget _buildTripTypeCard({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: AppColors.globalWhite,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected
                ? AppColors.primaryColorGlobal
                : AppColors.grey200,
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Column(
          children: [
            Stack(
              alignment: Alignment.topRight,
              children: [
                Container(
                  width: double.infinity,
                  child: Icon(
                    icon,
                    size: 40,
                    color: isSelected
                        ? AppColors.primaryColorGlobal
                        : AppColors.grey400,
                  ),
                ),
                if (isSelected)
                  Container(
                    padding: EdgeInsets.all(2),
                    decoration: BoxDecoration(
                      color: AppColors.primaryColorGlobal,
                      shape: BoxShape.circle,
                    ),
                    child: Icon(
                      Icons.check,
                      size: 16,
                      color: AppColors.globalWhite,
                    ),
                  ),
              ],
            ),
            SizedBox(height: 12),
            Text(
              title,
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: AppColors.globalBlack,
              ),
            ),
            SizedBox(height: 4),
            Text(
              subtitle,
              style: TextStyle(fontSize: 13, color: AppColors.grey600),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPeopleCounter(CreateAdventureFormState formState) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 20, vertical: 16),
      decoration: BoxDecoration(
        color: AppColors.grey50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.grey200),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            onPressed: () {
              ref
                  .read(createAdventureFormProvider.notifier)
                  .decrementMaxPeople();
            },
            icon: Icon(Icons.remove, color: AppColors.primaryColorGlobal),
            style: IconButton.styleFrom(
              backgroundColor: AppColors.globalWhite,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
          Text(
            '${formState.maxPeople}',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.w600,
              color: AppColors.globalBlack,
            ),
          ),
          IconButton(
            onPressed: () {
              ref
                  .read(createAdventureFormProvider.notifier)
                  .incrementMaxPeople();
            },
            icon: Icon(Icons.add, color: AppColors.globalWhite),
            style: IconButton.styleFrom(
              backgroundColor: AppColors.primaryColorGlobal,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDescriptionField(CreateAdventureFormState formState) {
    return Column(
      children: [
        TextField(
          controller: _descriptionController,
          onChanged: (value) {
            ref
                .read(createAdventureFormProvider.notifier)
                .updateDescription(value);
          },
          maxLines: 5,
          maxLength: 300,
          decoration: InputDecoration(
            hintText: AppStrings.descriptionHint,
            hintStyle: TextStyle(color: AppColors.grey400, fontSize: 14),
            filled: true,
            fillColor: AppColors.grey50,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppColors.grey200),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppColors.grey200),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(
                color: AppColors.primaryColorGlobal,
                width: 2,
              ),
            ),
            counterStyle: TextStyle(color: AppColors.grey500),
          ),
        ),
      ],
    );
  }

  Widget _buildSafetyMessage() {
    return Container(
      padding: EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.blueish,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.verified_user,
            color: AppColors.primaryColorGlobal,
            size: 20,
          ),
          SizedBox(width: 12),
          Expanded(
            child: Text(
              AppStrings.safetyMessage,
              style: TextStyle(
                fontSize: 13,
                color: AppColors.grey700,
                height: 1.4,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSaveButton(
    CreateAdventureFormState formState,
    CreateAdventureState createAdventureState,
  ) {
    final isButtonEnabled =
        formState.isValid && !createAdventureState.isLoading;
    return SizedBox(
      width: double.infinity,
      height: 56,
      child: ElevatedButton(
        onPressed: isButtonEnabled ? () => handleCreate() : null,
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primaryColorGlobal,
          disabledBackgroundColor: AppColors.grey300,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
        child: (createAdventureState.isLoading)
            ? CircularProgressIndicator(color: Colors.white)
            : Text(
                AppStrings.saveAdventure,
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: AppColors.globalWhite,
                ),
              ),
      ),
    );
  }
}
