/*
 * Copyright (c) 2016 Juniper Networks, Inc. All rights reserved.
 */
package ordt.parameters;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import ordt.annotate.AnnotateCommand;
import ordt.annotate.AnnotateSetCommand;
import ordt.extract.Ordt;
import ordt.extract.RegNumber;
import ordt.extract.ModComponent.CompType;
import ordt.parse.parameters.ExtParmsBaseListener;
import ordt.parse.parameters.ExtParmsLexer;
import ordt.parse.parameters.ExtParmsParser;

/**
 *  @author snellenbach      
 *  Jul 12, 2014
 *
 */
public class ExtParameters extends ExtParmsBaseListener  {
	private static List<String> parmFiles;

	// standard typed parameter set
	private static HashMap<String, ExtParameter<?>> params = new HashMap<String, ExtParameter<?>>();
	
	
	public enum SVBlockSelectModes { INTERNAL, EXTERNAL, ALWAYS } 
	public enum SVDecodeInterfaceTypes { NONE, LEAF, SERIAL8, RING8, RING16, RING32, PARALLEL, ENGINE1} 
	public enum SVChildInfoModes { PERL, MODULE } 
	
	// non-standard typed parameters
	private static SVDecodeInterfaceTypes sysVerRootDecoderInterface;
	private static SVDecodeInterfaceTypes sysVerSecondaryDecoderInterface;
	private static SVBlockSelectModes systemverilogBlockSelectMode;  
	private static SVChildInfoModes sysVerChildInfoMode;  

	private static int maxInternalRegReps = 4096;  // max internal reg reps allowed (not set externally)
	
	// list of model annotation commands
	private static List<AnnotateCommand> annotations = new ArrayList<AnnotateCommand>();
	
	public ExtParameters() {
	}
	
	/** initialize all parameters */
	public static void init() {
		
		// ---- global defaults
		params.put("min_data_size", new ExtIntegerParameter("min_data_size", 32) {  // special handling for min_data_size
			@Override
			public void set(String valStr) {
				Integer intval = Utils.strToInteger(valStr);
				if (intval != null) {
					if (!Utils.isPowerOf2(intval) || !Utils.isInRange(intval, 32, 1024))  
						Ordt.errorMessage("invalid minimum data size (" + intval + ").  Must be power of 2 and >=32.");
					else value = intval;
				} 
				else Ordt.errorMessage("invalid minimum data size specified (" + value + ").");
			}
		});
		initRegNumberParameter("base_address", new RegNumber(0)); 
		initRegNumberParameter("secondary_base_address", null); 
		initRegNumberParameter("secondary_low_address", null); 
		initRegNumberParameter("secondary_high_address", null); 
		initBooleanParameter("secondary_on_child_addrmaps", false); 
		initBooleanParameter("use_js_address_alignment", true); 
		initBooleanParameter("suppress_alignment_warnings", false); 
		initStringParameter("default_base_map_name", "");  
		initBooleanParameter("allow_unordered_addresses", false); 
		params.put("debug_mode", new ExtIntegerParameter("debug_mode", 0) {  // special handling for debug_mode
			@Override
			public void set(String valStr) {
				Integer intval = Utils.strToInteger(valStr);
				if (intval != null) {
					value = intval;
					if (intval != 0) Ordt.warnMessage("debug_mode parameter is set.  Non-standard ordt behavior can occur.");
				} 
				else Ordt.errorMessage("invalid debug_mode specified (" + value + ").");
			}
		});
		
		// ---- rdl input defaults
		initStringListParameter("process_component", new ArrayList<String>());
		initBooleanParameter("resolve_reg_category", false); 
		initBooleanParameter("restrict_defined_property_names", true); 
		
		// ---- jspec input defaults
		initStringListParameter("process_typedef", new ArrayList<String>());
		initBooleanParameter("root_regset_is_addrmap", false); 
		initBooleanParameter("root_is_external_decode", true); 
		initIntegerParameter("external_replication_threshold", getMaxInternalRegReps()); 	
		
		// ---- systemverilog output defaults
		initIntegerParameter("leaf_address_size", 40); 	
		sysVerRootDecoderInterface = SVDecodeInterfaceTypes.LEAF;
		sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.NONE;
		initBooleanParameter("base_addr_is_parameter", false); 
		initStringParameter("module_tag", "");
		initBooleanParameter("use_gated_logic_clock", false);
		initIntegerParameter("gated_logic_access_delay", 6); 	
		systemverilogBlockSelectMode = SVBlockSelectModes.EXTERNAL;  
		initBooleanParameter("export_start_end", false);
		initBooleanParameter("always_generate_iwrap", false);
		initBooleanParameter("suppress_no_reset_warnings", false); 
		initBooleanParameter("generate_child_addrmaps", false); 
		initIntegerParameter("ring_inter_node_delay", 0); 	
		initBooleanParameter("bbv5_timeout_input", false); 
		initBooleanParameter("include_default_coverage", false);
		sysVerChildInfoMode = SVChildInfoModes.PERL;  

		// ---- rdl output defaults
		initBooleanParameter("root_component_is_instanced", true); 
		initBooleanParameter("output_jspec_attributes", false);

		// ---- jspec output defaults
		initBooleanParameter("root_regset_is_instanced", true); 
		initStringListParameter("add_js_include", new ArrayList<String>());
		initBooleanParameter("no_root_enum_defs", false); 
		
		// ---- reglist output defaults
		initBooleanParameter("display_external_regs", true); 
		initBooleanParameter("show_reg_type", false);
		initStringParameter("match_instance", null);
		initBooleanParameter("show_fields", false);
		
		// ---- uvmregs output defaults
		initBooleanParameter("suppress_no_category_warnings", false); 
		initIntegerParameter("is_mem_threshold", 1000);
		initBooleanParameter("include_address_coverage", false); 
		initIntegerParameter("max_reg_coverage_bins", 128);
		
		// ---- bench output defaults
		initStringListParameter("add_test_command", new ArrayList<String>());
		initBooleanParameter("generate_external_regs", false); 
		initBooleanParameter("only_output_dut_instances", false); 
		initIntegerParameter("total_test_time", 5000);
	}
	
	static void initBooleanParameter(String name, Boolean value) {
		params.put(name, new ExtBooleanParameter(name, value));
	}
	
	static Boolean getBooleanParameter(String name) {
		return (Boolean) params.get(name).get();
	}
	
	static void initIntegerParameter(String name, Integer value) {
		params.put(name, new ExtIntegerParameter(name, value));
	}
	
	static Integer getIntegerParameter(String name) {
		return (Integer) params.get(name).get();
	}
	
	static void initRegNumberParameter(String name, RegNumber value) {
		params.put(name, new ExtRegNumberParameter(name, value));
	}
	
	static RegNumber getRegNumberParameter(String name) {
		return (RegNumber) params.get(name).get();
	}
	
	static void initStringParameter(String name, String value) {
		params.put(name, new ExtStringParameter(name, value));
	}
	
	static String getStringParameter(String name) {
		return (String) params.get(name).get();
	}
	
	static void initStringListParameter(String name, List<String> value) {
		params.put(name, new ExtStringListParameter(name, value));
	}
	
	@SuppressWarnings("unchecked")
	static List<String> getStringListParameter(String name) {
		return (List<String>) params.get(name).get();
	}
	
	@SuppressWarnings("unchecked")
	static Boolean hasStringListParameter(String name) {
		return !((List<String>) params.get(name).get()).isEmpty();
	}
	
	/**
	 * read parameters from specified parms files
	 */
	public static void loadParameters(List<String> inputParmFiles) {
		// save the parameter input file names
		parmFiles = inputParmFiles;
		
    	if (parmFiles.isEmpty()) Ordt.warnMessage("No parameters file specified.  Default or inline defined parameters will be used.");

		// read parameters from each file in list
		for (String inputFile: parmFiles) {
			ReadExtParameters(inputFile);
		}
	}
	
	/**
	 * read parameters from specified file  
	 */
	public static void ReadExtParameters(String inputParmFile) {		
    	System.out.println("Ordt: reading parameters from " + inputParmFile + "...");
        try {
        	// need to create an instance to be used as parse listener
        	ExtParameters inParms = new ExtParameters();
        	
        	InputStream is = System.in;
        	if ( inputParmFile!=null ) is = new FileInputStream(inputParmFile);
        
        	ANTLRInputStream input = new ANTLRInputStream(is);
        	ExtParmsLexer lexer = new ExtParmsLexer(input);

        	CommonTokenStream tokens = new CommonTokenStream(lexer);

        	ExtParmsParser parser; 
        	parser = new ExtParmsParser(tokens);

        	ParseTree tree = parser.ext_parms_root(); 
        	//System.out.println(tree.toStringTree());
        	ParseTreeWalker walker = new ParseTreeWalker(); // create standard
        	walker.walk(inParms, tree); // initiate walk of tree with listener
        	if (parser.getNumberOfSyntaxErrors() > 0) {
        		Ordt.errorExit("Parameter file parser errors detected.");  
        		System.exit(8);
        	}
        	
        	//root.display(true);

        } catch (FileNotFoundException e) {
        	Ordt.errorExit("parameter file not found. "  + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }		
	}

	// ------------------- ExtParmsBaseListener override methods
	
	/**
	 * Assign global parameters
	 */
	@Override public void enterGlobal_parm_assign(@NotNull ExtParmsParser.Global_parm_assignContext ctx) {
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());
	
	}
	
	/**
	 * Assign rdl input parameters
	 */
	@Override public void enterRdl_in_parm_assign(@NotNull ExtParmsParser.Rdl_in_parm_assignContext ctx) { 
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());			
	}
	
	/**
	 * Assign jspec input parameters
	 */
	@Override public void enterJspec_in_parm_assign(@NotNull ExtParmsParser.Jspec_in_parm_assignContext ctx) { 
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());			
	}
	
	/**
	 * Assign systemverilog output parameters
	 */
	@Override public void enterSystemverilog_out_parm_assign(@NotNull ExtParmsParser.Systemverilog_out_parm_assignContext ctx) {
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());		
	}
	
	/**
	 * Assign rdl output parameters
	 */
	@Override public void enterRdl_out_parm_assign(@NotNull ExtParmsParser.Rdl_out_parm_assignContext ctx) { 
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());				
	}
	
	/**
	 * Assign jspec output parameters
	 */
	@Override public void enterJspec_out_parm_assign(@NotNull ExtParmsParser.Jspec_out_parm_assignContext ctx) {
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());		
	}
	
	/**
	 * Assign reglist output parameters
	 */
	@Override public void enterReglist_out_parm_assign(@NotNull ExtParmsParser.Reglist_out_parm_assignContext ctx) {
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());		
	}
	
	/**
	 * Assign uvmregs output parameters
	 */
	@Override public void enterUvmregs_out_parm_assign(@NotNull ExtParmsParser.Uvmregs_out_parm_assignContext ctx) {
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());		
	}
	
	/**
	 * Assign bench output parameters
	 */
	@Override public void enterBench_out_parm_assign(@NotNull ExtParmsParser.Bench_out_parm_assignContext ctx) {
		assignParameter(ctx.getChild(0).getText(), ctx.getChild(2).getText());		
	}

	/**
	 * Capture annotation command  
		 annotation_command
		   : ('set_reg_property' | 'set_field_property')
		     ID EQ STR
		     ('instances' | 'components')
		     STR
	 */
	@Override public void enterAnnotation_command(ExtParmsParser.Annotation_commandContext ctx) {
		//System.out.println("ExtParameters enterAnnotation_command: " + ctx.getText());
		processAnnotationCommand(ctx);
	}

	// -------------------

	public static void processAnnotationCommand(ParserRuleContext ctx) {
		// extract command info
		String cmdName = ctx.getChild(0).getText();
		// if a set command, then extract info and save command
		if ("set_reg_property".equals(cmdName) || "set_field_property".equals(cmdName)) {
			String propertyName = ctx.getChild(1).getText().replaceAll("\"", "");
			String propertyValue = ctx.getChild(3).getText().replaceAll("\"", "");
			boolean pathUsesComps = "components".equals(ctx.getChild(4).getText());
			String pathStr = ctx.getChild(5).getText().replaceAll("\"", "");
			CompType target = "set_field_property".equals(cmdName)? CompType.FIELD : CompType.REG;
			AnnotateSetCommand cmd = new AnnotateSetCommand(target, pathUsesComps, pathStr, propertyName, propertyValue);
			if (cmd != null) annotations.add(cmd);
		}
	}

	/** 
	 * Assign valid parameters
	 * @param parameter name
	 * @param parameter value
	 */
	public static void assignParameter(String name, String value) {
		//System.out.println("ExtParameters assignParameter: " + name + " = " + value);

        // first check list of std-typed parameters
		if (params.containsKey(name)) params.get(name).set(value);
		//else System.out.println("----- cant find param " + name);
		
		// ---- not a match for std types, so check others		
		else if (name.equals("root_has_leaf_interface")) {  // DEPRECATED 
			sysVerRootDecoderInterface = value.equals("true") ? SVDecodeInterfaceTypes.LEAF : SVDecodeInterfaceTypes.PARALLEL;
			Ordt.warnMessage("Use of control parameter 'root_has_leaf_interface' is deprecated. Use 'root_decoder_interface = leaf' instead.");
		}
		else if (name.equals("root_decoder_interface")) {  
			if (value.equals("leaf")) sysVerRootDecoderInterface = SVDecodeInterfaceTypes.LEAF;
			else if (value.equals("serial8")) sysVerRootDecoderInterface = SVDecodeInterfaceTypes.SERIAL8;
			else if (value.equals("ring8")) sysVerRootDecoderInterface = SVDecodeInterfaceTypes.RING8;
			else if (value.equals("ring16")) sysVerRootDecoderInterface = SVDecodeInterfaceTypes.RING16;
			else if (value.equals("ring32")) sysVerRootDecoderInterface = SVDecodeInterfaceTypes.RING32;
			else sysVerRootDecoderInterface = SVDecodeInterfaceTypes.PARALLEL;  // parallel interface is default
		}
		else if (name.equals("secondary_decoder_interface")) {  
			if (value.equals("leaf")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.LEAF;
			else if (value.equals("serial8")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.SERIAL8;
			else if (value.equals("ring8")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.RING8;
			else if (value.equals("ring16")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.RING16;
			else if (value.equals("ring32")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.RING32;
			else if (value.equals("paallel")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.PARALLEL;
			else if (value.equals("engine1")) sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.ENGINE1;
			else sysVerSecondaryDecoderInterface = SVDecodeInterfaceTypes.NONE;  // no interface is default
		}
		else if (name.equals("use_external_select")) {  // DEPRECATED 
			systemverilogBlockSelectMode = value.equals("true") ? SVBlockSelectModes.EXTERNAL : SVBlockSelectModes.INTERNAL;
			Ordt.warnMessage("Use of control parameter 'use_external_select' is deprecated. Use 'block_select_mode' instead.");
		}
		else if (name.equals("block_select_mode")) {  
			if (value.equals("internal")) systemverilogBlockSelectMode = SVBlockSelectModes.INTERNAL;
			else if (value.equals("external")) systemverilogBlockSelectMode = SVBlockSelectModes.EXTERNAL;
			else systemverilogBlockSelectMode = SVBlockSelectModes.ALWAYS;
		}

		else if (name.equals("child_info_mode")) {  
			if (value.equals("module")) sysVerChildInfoMode = SVChildInfoModes.MODULE;
			else sysVerChildInfoMode = SVChildInfoModes.PERL;
		}

		else if (name.equals("external_decode_is_root")) {   // DEPRECATED 
			Ordt.warnMessage("Use of control parameter 'external_decode_is_root' is deprecated.");
		}

		//else
		//	Ordt.errorMessage("invalid parameter detected (" + name + ").");
	}

	/** get parmFile
	 *  @return the parmFile
	 */
	public static List<String> getParmFiles() {
		return parmFiles;
	}

	// ----------------------------------- getters ------------------------------------------

	/* return max allowed internal register replications */
	public static int getMaxInternalRegReps() {
		return maxInternalRegReps;
	}
	
	public static List<AnnotateCommand> getAnnotations() {
		return annotations;
	}

	/** get leafAddressSize
	 *  @return the leafAddressSize
	 */
	public static int getLeafAddressSize() {
		return getIntegerParameter("leaf_address_size");
	}

	/** get leafMinDataSize
	 *  @return the leafMinDataSize (bits)
	 */
	public static Integer getMinDataSize() {
		return getIntegerParameter("min_data_size");
	}
	
	/** get root decoder baseAddress */
	public static RegNumber getPrimaryBaseAddress() {
		return getRegNumberParameter("base_address");
	}
	
	/** true if decoder has non-null secondary decoder intf baseAddress */
	public static boolean hasSecondaryBaseAddress() {
		return getSecondaryBaseAddress() != null;
	}
	
	/** true if decoder has non-null secondary decoder intf min allowed address */
	public static boolean hasSecondaryLowAddress() {
		return getSecondaryLowAddress() != null;
	}
	
	/** true if decoder has non-null secondary decoder intf max allowed address */
	public static boolean hasSecondaryHighAddress() {
		return getSecondaryHighAddress() != null;
	}
	
	/** get secondary decoder intf baseAddress */
	public static RegNumber getSecondaryBaseAddress() {
		return getRegNumberParameter("secondary_base_address");
	}
	
	/** get secondary decoder intf min allowed address */
	public static RegNumber getSecondaryLowAddress() {
		return getRegNumberParameter("secondary_low_address");
	}
	
	/** get secondary decoder intf max allowed address */
	public static RegNumber getSecondaryHighAddress() {
		return getRegNumberParameter("secondary_high_address");
	}
	
	/** true if secondary interfaces should be created on child address map decoders */
	public static Boolean secondaryOnChildAddrmaps() {
		return getBooleanParameter("secondary_on_child_addrmaps");
	}

	/** get useJsAddressAlignment
	 */
	public static Boolean useJsAddressAlignment() {
		return getBooleanParameter("use_js_address_alignment");  
	}

	/** get suppressAlignmentWarnings  
	 */
	public static Boolean suppressAlignmentWarnings() {
		return getBooleanParameter("suppress_alignment_warnings");
	}
	
	/** get allowUnorderedAddresses  
	 */
	public static Boolean allowUnorderedAddresses() {
		return getBooleanParameter("allow_unordered_addresses");
	}

	/** get defaultBaseMapName
	 *  @return the defaultBaseMapName
	 */
	public static String defaultBaseMapName() {
		return getStringParameter("default_base_map_name");
	}

	/** get debugMode
	 *  @return the debugMode (non-zero indicates debug)
	 */
	public static Integer getDebugMode() {
		return getIntegerParameter("debug_mode");
	}

	/** returns true if comp names have been specified for processing */
	public static boolean hasRdlProcessComponents() {
		return hasStringListParameter("process_component");
	}
	
	/** get list of comp to be processed
	 *  @return the jspecProcessTypedef
	 */
	public static List<String> getRdlProcessComponents() {
		return getStringListParameter("process_component");
	}

	/** returns true if reg categories should be deduced from rdl settings */
	public static boolean rdlResolveRegCategory() {
		return getBooleanParameter("resolve_reg_category");
	}
	
	/** returns true if user defined rdl properties should always start with 'p_' */
	public static boolean rdlRestrictDefinedPropertyNames() {
		return getBooleanParameter("restrict_defined_property_names");
	}
	
	// -------- js input getters

	/** returns true if typedef names have been specified for processing */
	public static boolean hasJspecProcessTypedefs() {
		return hasStringListParameter("process_typedef");
	}
	
	/** get list of typedefs to be processed
	 *  @return the jspecProcessTypedef
	 */
	public static List<String> getJspecProcessTypedefs() {
		return getStringListParameter("process_typedef");
	}

	/** get jspecRootRegsetIsAddrmap
	 *  @return the jspecRootRegsetIsAddrmap
	 */
	public static Boolean jspecRootRegsetIsAddrmap() {
		return getBooleanParameter("root_regset_is_addrmap");
	}

	/** get jspecRootIsExternalDecode
	 *  @return the jspecRootIsExternalDecode
	 */
	public static Boolean jspecRootIsExternalDecode() {
		return getBooleanParameter("root_is_external_decode");
	}
	
	/** get jspecExternalReplicationThreshold
	 *  @return the jspecExternalReplicationThreshold
	 */
	public static int jspecExternalReplicationThreshold() {
		return getIntegerParameter("external_replication_threshold");
	}

	// -------- system verilog getters
	
	public static SVDecodeInterfaceTypes getSysVerRootDecoderInterface() {
		return sysVerRootDecoderInterface;
	}
	
	public static SVDecodeInterfaceTypes getSysVerSecondaryDecoderInterface() {
		return sysVerSecondaryDecoderInterface;
	}

	/** get baseAddrIsParameter
	 */
	public static Boolean systemverilogBaseAddrIsParameter() {
		return getBooleanParameter("base_addr_is_parameter");
	}

	/** get getSystemverilogModuleTag
	 */
	public static String getSystemverilogModuleTag() {
		return getStringParameter("module_tag");
	}

	/** get systemverilogUseGatedLogicClk
	 */
	public static Boolean systemverilogUseGatedLogicClk() {
		return getBooleanParameter("use_gated_logic_clock");
	}

	/** get systemverilogGatedLogicAccessDelay
	 */
	public static Integer systemverilogGatedLogicAccessDelay() {
		return getIntegerParameter("gated_logic_access_delay");
	}

	/** get systemverilogExportStartEnd
	 */
	public static Boolean systemverilogExportStartEnd() {
		return getBooleanParameter("export_start_end");
	}

	/** get systemverilogAlwaysGenerateIwrap
	 */
	public static Boolean sysVerilogAlwaysGenerateIwrap() {
		return getBooleanParameter("always_generate_iwrap");
	}

	/** get systemverilogBlockSelectMode */
	public static SVBlockSelectModes getSystemverilogBlockSelectMode() {
		return systemverilogBlockSelectMode;
	}

	/** get sysVerChildInfoMode */
	public static SVChildInfoModes getSysVerChildInfoMode() {
		return sysVerChildInfoMode;
	}
	
	/** get sysVerSuppressNoResetWarnings  
	 */
	public static Boolean sysVerSuppressNoResetWarnings() {
		return getBooleanParameter("suppress_no_reset_warnings");
	}
	
	/** get sysVerGenerateChildAddrmaps
	 */
	public static Boolean sysVerGenerateChildAddrmaps() {
		return getBooleanParameter("generate_child_addrmaps");
	}
	
	/** get sysVerRingInterNodeDelay
	 *  @return the sysVerRingInterNodeDelay
	 */
	public static int sysVerRingInterNodeDelay() {
		return getIntegerParameter("ring_inter_node_delay");
	}
	
	public static Boolean sysVerBBV5TimeoutInput() {
		return getBooleanParameter("bbv5_timeout_input");
	}
	
	public static Boolean sysVerIncludeDefaultCoverage() {
		return getBooleanParameter("include_default_coverage");
	}
	
	public static Boolean sysVerGenerateExternalRegs() {
		return getBooleanParameter("generate_external_regs");
	}

	/** get rdlRootComonentIsInstanced
	 *  @return the rdlRootComonentIsInstanced
	 */
	public static Boolean rdlRootComponentIsInstanced() {
		return getBooleanParameter("root_component_is_instanced");
	}

	/** get rdlOutputJspecAttributes
	 *  @return the rdlOutputJspecAttributes
	 */
	public static Boolean rdlOutputJspecAttributes() {
		return getBooleanParameter("output_jspec_attributes");
	}

	/** get rdlNoRootEnumDefs
	 */
	public static Boolean rdlNoRootEnumDefs() {
		return getBooleanParameter("no_root_enum_defs");
	}

	/** get jspecRootRegsetIsInstanced
	 *  @return the jspecRootRegsetIsInstanced
	 */
	public static Boolean jspecRootRegsetIsInstanced() {
		return getBooleanParameter("root_regset_is_instanced");
	}

	/** get jspecIncludeFiles
	 *  @return the jspecIncludeFiles
	 */
	public static List<String> getJspecIncludeFiles() {
		return getStringListParameter("add_js_include");
	}

	/** get reglistDisplayExternalRegs
	 *  @return the reglistDisplayExternalRegs
	 */
	public static Boolean reglistDisplayExternalRegs() {
		return getBooleanParameter("display_external_regs");
	}

	/** get reglistShowRegType
	 *  @return the reglistShowRegType
	 */
	public static Boolean reglistShowRegType() {
		return getBooleanParameter("show_reg_type");
	}

	public static String getReglistMatchInstance() {
		return getStringParameter("match_instance");
	}

	public static Boolean reglistShowFields() {
		return getBooleanParameter("show_fields");
	}

	public static Boolean uvmregsSuppressNoCategoryWarnings() {
		return getBooleanParameter("suppress_no_category_warnings");
	}

	/** get uvmregsIsMemThreshold
	 *  @return the uvmregsIsMemThreshold
	 */
	public static Integer uvmregsIsMemThreshold() {
		return getIntegerParameter("is_mem_threshold");
	}

	public static Boolean uvmregsIncludeAddressCoverage() {
		return getBooleanParameter("include_address_coverage");
	}

	public static int uvmregsMaxRegCoverageBins() {
		return getIntegerParameter("max_reg_coverage_bins");
	}

	/** returns true if test commands have been specified  */
	public static boolean hasTestCommands() {
		return hasStringListParameter("add_test_command");
	}
	
	/** get list of test commands to be processed
	 */
	public static List<String> getTestCommands() {
		return getStringListParameter("add_test_command");
	}
	
	public static Boolean benchOnlyOutputDutInstances() {
		return getBooleanParameter("only_output_dut_instances");
	}
	
	public static int benchTotalTestTime() {
		return getIntegerParameter("total_test_time");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		List<String> inFiles = new ArrayList<String>();
		ExtParameters.loadParameters(inFiles);
		//System.out.println("leafAddressSize=" + getLeafAddressSize());
		//System.out.println("leafMinDataSize=" + getMinDataSize());
		//System.out.println("leafMaxDataSize=" + getLeafMaxDataSize());
		//System.out.println("leafBaseAddress=" + getLeafBaseAddress());
		for (String key: params.keySet()) {
			System.out.println("key=" + key + ", value=" + params.get(key));
		}
		System.out.println("has rdl proc comps=" + hasRdlProcessComponents());

	}

}
