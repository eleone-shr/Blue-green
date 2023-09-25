def req = ["env":"",
                   "changeRequest":""]
def env = '$(env)'
def buildID = '$(buildID)'
def Recipients = "examplename1@exampleemail.com, examplename2@exampleemail.com"

def Yamldata = [
    'apiVersion': 'v1',
    'environment': env,
    'version': 1.0,
    'appFamily': 'iep-test',
    'appName': 'bg-sample',
    'buildID': buildID,
    'kind': 'deployment',
    'manifest': 'lines',
    'deployment': [ 
        'target': 'EKS',
        'style': 'non-intrusive',
        'window': 'asap'
    ],
    'email' : 'emails'
]

node {
    stage('Write Yaml') {
        writeYaml file: 'manifest.yaml', data: Yamldata, overwrite: true
        sh 'cat manifest.yaml'
        archiveArtifacts artifacts: "manifest.yaml", fingerprint: true
    }

    stage('Env Select') {
        envTestList = ["DSIT","SITE","PTE","PROD","BLUE-GREEN","END"]
		
		req = ""
		
        req = input message: "Select an environment to deploy Artifact?",
            id: 'envResponse',
                parameters: [string(description: 'Change Request number from KISAM', name: 'changeRequest'),
                        choice(name: 'env',
                        choices: envTestList)]
        println(req)

        if (req.env == 'BLUE-GREEN') {
            echo 'Blue-green deployment selected'
            iepBlueGreen()
        }
    }
}

def iepBlueGreen() {
    stage('Blue-Green Deploy') {

        copyArtifacts(projectName: env.JOB_NAME, selector: specific(env.BUILD_NUMBER), filter: 'manifest.yaml')

        def file = readYaml file: "manifest.yaml"

        response = input message: "Fill out the following for a blue-green manifest.",
                        id: 'bgResponse',
                        parameters: [string(description: 'Current Version', name: 'currentVersion'),
                                    string(description: 'Updating Version', name: 'updatingVersion'),
                                    string(description: 'Current Build ID', name: 'currentBuildID'),
                                    string(description: 'Updating Build ID', name: 'updatingBuildID'),
                                    string(description: 'Blue Weight', name: 'blueWeight'),
                                    string(description: 'Green Weight', name: 'greenWeight'),
                                    choice(name: 'environment',
                                    choices: envTestList)]
        println(response)

        file['environment'] = response["environment"].toLowerCase()
        file['version'] = response["currentVersion"] + "," + response["updatingVersion"]
        file['buildID'] = response["currentBuildID"] + "," + response["updatingBuildID"]
        file['kind'] = "promotion"

        file['deployment'] = ["type": "BG",
                              "blue": ["weight": response["blueWeight"]],
                              "green": ["weight": response["greenWeight"]]]

        sh """
                if [ -f manifest.yaml ] ; then
                        rm -f manifest.yaml
                fi
        """

        writeYaml file: 'manifest.yaml', data: file, overwrite:true

        sh 'cat manifest.yaml'
    }
}
                                  
